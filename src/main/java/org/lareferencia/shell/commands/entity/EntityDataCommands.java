/*
 *   Copyright (c) 2013-2022. LA Referencia / Red CLARA and others
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *   This file is part of LA Referencia software platform LRHarvester v4.x
 *   For any further information please contact Lautaro Matas <lmatas@gmail.com>
 */
package org.lareferencia.shell.commands.entity;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.net.ssl.SSLContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.core.entity.domain.EntityRelationException;
import org.lareferencia.core.entity.services.EntityDataService;
import org.lareferencia.core.entity.services.EntityLoadingMonitorService;
import org.lareferencia.core.entity.services.EntityLoadingStats;
import org.lareferencia.core.entity.services.exception.EntitiyRelationXMLLoadingException;
import org.lareferencia.core.util.Profiler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.w3c.dom.Document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;


@ShellComponent
public class EntityDataCommands {
	
	private static Logger logger = LogManager.getLogger(EntityDataCommands.class);

	private static final String REMOVE_DELETED_RELATIONS_SCRIPT =
			"boolean changed = false; "
			+ "if (params.relationFields != null && params.relationFields.size() > 0) { "
			+ "  for (def relationField : params.relationFields) { "
			+ "    if (ctx._source.containsKey(relationField)) { "
			+ "      def value = ctx._source[relationField]; "
			+ "      if (value instanceof List) { "
			+ "        for (int i = value.size() - 1; i >= 0; i--) { "
			+ "          def item = value.get(i); "
			+ "          if (item instanceof Map && item.containsKey('id') && params.deletedIds.containsKey(item.id)) { "
			+ "            value.remove(i); "
			+ "            changed = true; "
			+ "          } "
			+ "        } "
			+ "      } "
			+ "    } "
			+ "  } "
			+ "} else { "
			+ "  for (def entry : ctx._source.entrySet()) { "
			+ "    def value = entry.getValue(); "
			+ "    if (value instanceof List) { "
			+ "      for (int i = value.size() - 1; i >= 0; i--) { "
			+ "        def item = value.get(i); "
			+ "        if (item instanceof Map && item.containsKey('id') && params.deletedIds.containsKey(item.id)) { "
			+ "          value.remove(i); "
			+ "          changed = true; "
			+ "        } "
			+ "      } "
			+ "    } "
			+ "  } "
			+ "} "
			+ "if (!changed) { ctx.op = 'noop'; }";
	
	static javax.xml.parsers.DocumentBuilder dBuilder;

	private final ObjectMapper jsonMapper = new ObjectMapper();
		
	// Utilizar ThreadLocal para almacenar la instancia de DocumentBuilder por hilo
	private static final ThreadLocal<DocumentBuilder> threadLocalDocumentBuilder = ThreadLocal.withInitial(() -> {
		try {
			return DocumentBuilderFactory.newInstance().newDocumentBuilder();
		} catch (Exception e) {
			throw new RuntimeException("Error al crear DocumentBuilder", e);
		}
	});
	
	@Autowired
	EntityDataService erService;

	@Autowired
	private EntityLoadingMonitorService entityLoadingMonitorService;

	@Value("${elastic.host:localhost}")
	private String host;

	@Value("${elastic.port:9200}")
	private Integer port;

	@Value("${elastic.username:admin}")
	private String username;

	@Value("${elastic.password:admin}")
	private String password;

	@Value("${elastic.useSSL:false}")
	private Boolean useSSL;

	@Value("${elastic.authenticate:false}")
	private Boolean authenticate;


	@ShellMethod("Load entity-relation data from xml. If path points to a directory all contained .xml files will be loaded, otherwise only referenced file will be loaded")
    public String load_data(@ShellOption(value = "--path", defaultValue = "false") String path,
                            @ShellOption(value = "--dryRun", defaultValue = "false") String dryRun,
                            @ShellOption(value = "--doProfile", defaultValue = "false") String doProfile,
                            @ShellOption(value = "--threadsToRun", defaultValue = "1", help = "This option is deprecated and will be ignored.") int threadsToRun) throws Exception {

        // Validate path parameter
        if (path == null || path.equals("false") || path.trim().isEmpty()) {
            String errorMsg = "ERROR: Path parameter is required. Please provide a valid file or directory path using --path option.";
            logger.error(errorMsg);
            return errorMsg;
        }

        File targetPath = new File(path);
        
        // Check if path exists
        if (!targetPath.exists()) {
            String errorMsg = String.format("ERROR: Path does not exist: %s", path);
            logger.error(errorMsg);
            return errorMsg;
        }

        // Check if path is readable
        if (!targetPath.canRead()) {
            String errorMsg = String.format("ERROR: Path is not readable: %s", path);
            logger.error(errorMsg);
            return errorMsg;
        }

        Boolean dryRunMode = Boolean.parseBoolean(dryRun);
        
        logger.info("==================================================");
        logger.info("ENTITY DATA LOADING PROCESS STARTED");
        logger.info("==================================================");
        logger.info("Target path: {}", path);
        logger.info("Path type: {}", targetPath.isDirectory() ? "DIRECTORY" : "FILE");
        logger.info("Dry-run mode: {}", dryRunMode ? "ENABLED (no data will be persisted)" : "DISABLED (data will be persisted)");
        logger.info("==================================================");

        Profiler generalProfiler = new Profiler(true, "\nPath: " + path + " ").start();

        // Count XML files before processing
        int xmlFileCount = countXmlFiles(targetPath);
        
        if (xmlFileCount == 0) {
            String warningMsg = String.format("WARNING: No XML files found to process in path: %s", path);
            logger.warn(warningMsg);
            logger.info("==================================================");
            logger.info("ENTITY DATA LOADING PROCESS FINISHED");
            logger.info("Status: NO FILES TO PROCESS");
            logger.info("==================================================");
            return warningMsg;
        }

        logger.info("Found {} XML file(s) to process", xmlFileCount);
        logger.info("--------------------------------------------------");
        logger.info("Starting file processing...");
        logger.info("--------------------------------------------------");

        // Process XML files
        processFiles(targetPath, dryRunMode);

		logger.info("--------------------------------------------------");
		logger.info("File processing completed. Running post-processing tasks...");
		logger.info("--------------------------------------------------");
		
		logger.info("Dont forget to run MERGE Process!!!!!!");
		

		// Set loading process as finished
		entityLoadingMonitorService.setLoadingProcessInProgress(false);

		// Write to JSON
		logger.info("Writing loading report to JSON...");
		entityLoadingMonitorService.writeToJSON(path);

		logger.info("==================================================");
		logger.info("ENTITY DATA LOADING PROCESS FINISHED");
		logger.info("==================================================");

		generalProfiler.report(logger);
		return entityLoadingMonitorService.loadingReport();
    }


	private int countXmlFiles(File file) {
		if (!file.exists()) {
			return 0;
		}
		
		if (file.isFile()) {
			return file.getName().endsWith(".xml") ? 1 : 0;
		}
		
		if (file.isDirectory()) {
			int count = 0;
			File[] files = file.listFiles();
			
			if (files == null || files.length == 0) {
				return 0;
			}
			
			for (File subFile : files) {
				count += countXmlFiles(subFile);
			}
			return count;
		}
		
		return 0;
	}

	private void processFiles(File file, Boolean dryRunMode) {
		if (!file.exists()) {
			logger.warn("Skipping non-existent path: {}", file.getAbsolutePath());
			return;
		}
		
        if (file.isDirectory()) {
			File[] files = file.listFiles();
			
			if (files == null || files.length == 0) {
				logger.debug("Empty directory: {}", file.getAbsolutePath());
				return;
			}
			
            for (File subFile : files) {
                processFiles(subFile, dryRunMode);
            }
        } else if (file.isFile() && file.getName().endsWith(".xml")) {
            load_xml_file(file, dryRunMode);
        } else if (file.isFile()) {
			logger.debug("Skipping non-XML file: {}", file.getName());
		}
    }	

	

	
	
	
	private void load_xml_file(File file, Boolean dryRun) {
		
		logger.info("Processing file: {}", file.getName());
		
		try {
			if (!file.canRead()) {
				logger.error("ERROR: File is not readable: {}", file.getAbsolutePath());
				entityLoadingMonitorService.incrementTotalFailedFiles();
				return;
			}

			InputStream input = new FileInputStream(file);

			// Increment total processed files
			entityLoadingMonitorService.incrementTotalProcessedFiles();

			DocumentBuilder dBuilder = threadLocalDocumentBuilder.get();
		
			Document doc = dBuilder.parse(input);
			
			EntityLoadingStats stats = erService.parseAndPersistEntityRelationDataFromXMLDocument(doc, dryRun);
			entityLoadingMonitorService.reportEntityLoadingStats(stats);
			entityLoadingMonitorService.incrementTotalSuccessfulFiles();
			
			logger.info("SUCCESS: File processed successfully - {}", file.getName());
		
		} catch (EntitiyRelationXMLLoadingException e) {
			// Set file name for error reporting
			e.setFileName(file.getAbsolutePath());
			// Report exception 
			entityLoadingMonitorService.reportException(e);

			// The file was not loaded so increment total failed files
			entityLoadingMonitorService.incrementTotalFailedFiles();
			logger.error("FAILED: Entity-relation XML loading error - {} - Reason: {}", file.getName(), e.getMessage());
			
		} catch (EntityRelationException e) {
			entityLoadingMonitorService.incrementTotalFailedFiles();
			logger.error("FAILED: Entity-relation exception while loading file - {} - Reason: {}", file.getName(), e.getMessage());
			
		} catch (Exception e) {
			entityLoadingMonitorService.incrementTotalFailedFiles();
			logger.error("FAILED: Unexpected exception while loading file - {} - Reason: {}", file.getName(), e.getMessage());
			logger.debug("Stack trace:", e);
		} 
	}
	
	
	@ShellMethod("Merge dirty entities and relations: consolidates data from source_entity to entity tables, creating final entity and relation records")
	public String merge_dirty_entities() {
		
		logger.info("==================================================");
		logger.info("MERGE DIRTY ENTITIES AND RELATIONS PROCESS STARTED");
		logger.info("==================================================");
		logger.info("This process will consolidate all dirty entities and relations");
		logger.info("from source_entity tables to entity tables.");
		logger.info("--------------------------------------------------");
		
		try {
			Profiler profiler = new Profiler(true, "Merge Dirty Entities and Relations Process").start();
			
			// Execute merge process
			erService.mergeDirtyEntitiesAndRelations();
			
			profiler.report(logger);
			
			logger.info("==================================================");
			logger.info("MERGE DIRTY ENTITIES AND RELATIONS PROCESS COMPLETED SUCCESSFULLY");
			logger.info("==================================================");
			
			return "Merge process completed successfully";
			
		} catch (Exception e) {
			logger.error("==================================================");
			logger.error("MERGE DIRTY ENTITIES AND RELATIONS PROCESS FAILED");
			logger.error("==================================================");
			logger.error("ERROR: {}", e.getMessage());
			logger.error("Stack trace:", e);
			
			return "ERROR: Merge process failed - " + e.getMessage();
		}
	}

	@ShellMethod("Mark entities from a UUID file as deleted, so they are ignored by entity indexing")
	public String mark_entities_deleted(@ShellOption(value = "--path") String path) {
		return set_entities_deleted(path, "true");
	}

	@ShellMethod("Set entity deleted flag from a UUID file")
	public String set_entities_deleted(@ShellOption(value = "--path") String path,
			@ShellOption(value = "--deleted", defaultValue = "true") String deleted) {

		if (path == null || path.trim().isEmpty()) {
			return "ERROR: Path parameter is required. Please provide a UUID file path using --path option.";
		}

		Path uuidFilePath = Path.of(path);
		if (!Files.exists(uuidFilePath)) {
			return "ERROR: UUID file does not exist: " + path;
		}
		if (!Files.isRegularFile(uuidFilePath) || !Files.isReadable(uuidFilePath)) {
			return "ERROR: UUID file is not readable: " + path;
		}

		if (deleted == null || (!deleted.equalsIgnoreCase("true") && !deleted.equalsIgnoreCase("false"))) {
			return "ERROR: Deleted parameter must be true or false.";
		}

		boolean deletedValue = Boolean.parseBoolean(deleted);
		Set<UUID> entityIds = new LinkedHashSet<UUID>();
		List<String> invalidTokens = new ArrayList<String>();

		try {
			for (String line : Files.readAllLines(uuidFilePath)) {
				String cleanLine = line.split("#", 2)[0].trim();
				if (cleanLine.isEmpty())
					continue;

				for (String token : cleanLine.split("[,;\\s]+")) {
					if (token == null || token.trim().isEmpty())
						continue;

					try {
						entityIds.add(UUID.fromString(token.trim()));
					} catch (IllegalArgumentException e) {
						invalidTokens.add(token.trim());
					}
				}
			}

			if (entityIds.isEmpty()) {
				return "ERROR: No valid UUIDs found in file: " + path;
			}

			int updated = erService.updateEntitiesDeleted(entityIds, deletedValue);
			int notFound = entityIds.size() - updated;

			StringBuilder result = new StringBuilder();
			result.append("Entities deleted flag updated successfully");
			result.append(" | deleted=").append(deletedValue);
			result.append(" | valid UUIDs=").append(entityIds.size());
			result.append(" | updated=").append(updated);
			result.append(" | not found=").append(notFound);

			if (!invalidTokens.isEmpty()) {
				result.append(" | invalid tokens=").append(invalidTokens.size());
				logger.warn("Invalid UUID tokens ignored while updating deleted flag: {}", invalidTokens);
			}

			logger.info(result.toString());
			return result.toString();

		} catch (Exception e) {
			if (isMissingEntityDeletedColumnException(e)) {
				String message = "ERROR: Database schema is missing entity.deleted. Run database_migrate before using mark_entities_deleted.";
				logger.error(message, e);
				return message;
			}

			logger.error("ERROR: Failed to update entities deleted flag from file {}: {}", path, e.getMessage(), e);
			return "ERROR: Failed to update entities deleted flag - " + e.getMessage();
		}
	}

	@ShellMethod("Remove deleted entities and their nested references from an Elasticsearch/OpenSearch index")
	public String remove_deleted_entities_from_index(@ShellOption(value = "--indexName") String indexName,
			@ShellOption(value = "--pageSize", defaultValue = "1000") int pageSize,
			@ShellOption(value = "--timeoutSeconds", defaultValue = "300") int timeoutSeconds,
			@ShellOption(value = "--relationFields", defaultValue = "") String relationFields) {

		if (indexName == null || indexName.trim().isEmpty()) {
			return "ERROR: indexName parameter is required. Please provide an index name using --indexName option.";
		}
		if (indexName.contains("/") || indexName.contains("\\")) {
			return "ERROR: indexName must be a single Elasticsearch/OpenSearch index name.";
		}
		if (pageSize <= 0) {
			return "ERROR: pageSize must be greater than zero.";
		}
		if (timeoutSeconds <= 0) {
			return "ERROR: timeoutSeconds must be greater than zero.";
		}
		List<String> relationFieldList = parseRelationFields(relationFields);
		String relationFieldValidationError = validateRelationFields(relationFieldList);
		if (relationFieldValidationError != null) {
			return relationFieldValidationError;
		}

		logger.info("==================================================");
		logger.info("DELETED ENTITY INDEX CLEANUP STARTED");
		logger.info("==================================================");
		logger.info("Target index: {}", indexName);
		logger.info("Page size: {}", pageSize);
		logger.info("Request timeout: {} seconds", timeoutSeconds);
		logger.info("Relation fields: {}", relationFieldList.isEmpty() ? "all list fields" : relationFieldList);

		long deletedEntityIds = 0;
		long deletedRootDocuments = 0;
		long updatedRelationshipDocuments = 0;
		long noopRelationshipDocuments = 0;
		int page = 0;
		int batches = 0;

		try (RestClient elasticClient = buildElasticRestClient(timeoutSeconds)) {
			while (true) {
				List<UUID> deletedIdsPage = erService.getDeletedEntityIds(page, pageSize);
				if (deletedIdsPage.isEmpty())
					break;

				List<String> deletedIds = toStringIds(deletedIdsPage);
				deletedEntityIds += deletedIds.size();
				batches++;

				deletedRootDocuments += deleteRootDocumentsFromIndex(elasticClient, indexName, deletedIds);
				ElasticUpdateResult relationUpdate = removeDeletedRelationsFromIndex(elasticClient, indexName, deletedIds, relationFieldList);
				updatedRelationshipDocuments += relationUpdate.updated;
				noopRelationshipDocuments += relationUpdate.noops;

				logger.info("Cleanup batch {} completed | deleted IDs={} | root documents deleted={} | relationship documents updated={} | noops={}",
						batches, deletedIds.size(), deletedRootDocuments, updatedRelationshipDocuments, noopRelationshipDocuments);
				page++;
			}

			StringBuilder result = new StringBuilder();
			result.append("Deleted entity index cleanup completed");
			result.append(" | index=").append(indexName);
			result.append(" | deleted entity IDs=").append(deletedEntityIds);
			result.append(" | root documents deleted=").append(deletedRootDocuments);
			result.append(" | documents with relationships updated=").append(updatedRelationshipDocuments);
			result.append(" | relationship noops=").append(noopRelationshipDocuments);
			result.append(" | relation fields=").append(relationFieldList.isEmpty() ? "all list fields" : relationFieldList);
			result.append(" | batches=").append(batches);

			logger.info(result.toString());
			return result.toString();

		} catch (Exception e) {
			if (isMissingEntityDeletedColumnException(e)) {
				String message = "ERROR: Database schema is missing entity.deleted. Run database_migrate before using remove_deleted_entities_from_index.";
				logger.error(message, e);
				return message;
			}

			logger.error("ERROR: Failed to remove deleted entities from index {}: {}", indexName, e.getMessage(), e);
			return "ERROR: Failed to remove deleted entities from index - " + e.getMessage();
		}
	}

	private boolean isMissingEntityDeletedColumnException(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			String message = current.getMessage();
			if (message != null
					&& message.contains("column \"deleted\"")
					&& message.contains("relation \"entity\"")) {
				return true;
			}
			current = current.getCause();
		}

		return false;
	}

	private RestClient buildElasticRestClient(int timeoutSeconds) throws Exception {
		final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
		credentialsProvider.setCredentials(AuthScope.ANY,
				new UsernamePasswordCredentials(username.trim(), password.trim()));

		final SSLContext sslContext = SSLContexts.custom()
				.loadTrustMaterial(null, TrustAllStrategy.INSTANCE)
				.build();
		final int timeoutMillis = (int) Math.min(Integer.MAX_VALUE, timeoutSeconds * 1000L);

		RestClientBuilder builder = RestClient.builder(new HttpHost(host.trim(), port, useSSL ? "https" : "http"))
				.setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
						.setConnectTimeout(timeoutMillis)
						.setConnectionRequestTimeout(timeoutMillis)
						.setSocketTimeout(timeoutMillis))
				.setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
					@Override
					public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
						HttpAsyncClientBuilder builder = httpClientBuilder;

						if (useSSL)
							builder = builder.setSSLContext(sslContext);

						if (authenticate)
							builder = builder.setDefaultCredentialsProvider(credentialsProvider);

						return builder;
					}
				});

		return builder.build();
	}

	private List<String> toStringIds(List<UUID> entityIds) {
		List<String> result = new ArrayList<String>();
		for (UUID entityId : entityIds)
			result.add(entityId.toString());

		return result;
	}

	private List<String> parseRelationFields(String relationFields) {
		List<String> result = new ArrayList<String>();
		if (relationFields == null || relationFields.trim().isEmpty()) {
			return result;
		}

		String[] relationFieldNames = relationFields.split(",");
		for (String relationFieldName : relationFieldNames) {
			String trimmed = relationFieldName.trim();
			if (!trimmed.isEmpty()) {
				result.add(trimmed);
			}
		}

		return result;
	}

	private String validateRelationFields(List<String> relationFields) {
		for (String relationField : relationFields) {
			if (!relationField.matches("[A-Za-z0-9_-]+")) {
				return "ERROR: relationFields must be top-level relation object field names such as 'journal', not id subfields such as 'journal.id'.";
			}
		}

		return null;
	}

	private long deleteRootDocumentsFromIndex(RestClient elasticClient, String indexName, List<String> deletedIds)
			throws Exception {
		Map<String, Object> terms = new LinkedHashMap<String, Object>();
		terms.put("_id", deletedIds);

		Map<String, Object> query = new LinkedHashMap<String, Object>();
		query.put("terms", terms);

		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("query", query);

		JsonNode response = performJsonRequest(elasticClient, "POST", "/" + indexName + "/_delete_by_query", body);
		return response.path("deleted").asLong(0);
	}

	private ElasticUpdateResult removeDeletedRelationsFromIndex(RestClient elasticClient, String indexName,
			List<String> deletedIds, List<String> relationFields) throws Exception {
		Map<String, Boolean> deletedIdMap = new LinkedHashMap<String, Boolean>();
		for (String deletedId : deletedIds)
			deletedIdMap.put(deletedId, Boolean.TRUE);

		Map<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("deletedIds", deletedIdMap);
		params.put("relationFields", relationFields);

		Map<String, Object> script = new LinkedHashMap<String, Object>();
		script.put("lang", "painless");
		script.put("source", REMOVE_DELETED_RELATIONS_SCRIPT);
		script.put("params", params);

		Map<String, Object> query = buildDeletedRelationQuery(deletedIds, relationFields);

		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("script", script);
		body.put("query", query);

		JsonNode response = performJsonRequest(elasticClient, "POST", "/" + indexName + "/_update_by_query", body);
		return new ElasticUpdateResult(response.path("updated").asLong(0), response.path("noops").asLong(0));
	}

	private Map<String, Object> buildDeletedRelationQuery(List<String> deletedIds, List<String> relationFields) {
		if (relationFields.isEmpty()) {
			Map<String, Object> query = new LinkedHashMap<String, Object>();
			query.put("match_all", Collections.emptyMap());
			return query;
		}

		List<Map<String, Object>> should = new ArrayList<Map<String, Object>>();
		for (String relationField : relationFields) {
			Map<String, Object> terms = new LinkedHashMap<String, Object>();
			terms.put(relationField + ".id", deletedIds);

			Map<String, Object> termsQuery = new LinkedHashMap<String, Object>();
			termsQuery.put("terms", terms);
			should.add(termsQuery);
		}

		Map<String, Object> bool = new LinkedHashMap<String, Object>();
		bool.put("should", should);
		bool.put("minimum_should_match", 1);

		Map<String, Object> query = new LinkedHashMap<String, Object>();
		query.put("bool", bool);
		return query;
	}

	private JsonNode performJsonRequest(RestClient elasticClient, String method, String endpoint, Map<String, Object> body)
			throws Exception {
		Request request = new Request(method, endpoint);
		request.addParameter("conflicts", "proceed");
		request.addParameter("refresh", "true");
		request.setJsonEntity(jsonMapper.writeValueAsString(body));

		Response response = elasticClient.performRequest(request);
		return jsonMapper.readTree(response.getEntity().getContent());
	}

	private static class ElasticUpdateResult {
		long updated;
		long noops;

		ElasticUpdateResult(long updated, long noops) {
			this.updated = updated;
			this.noops = noops;
		}
	}
	
	
}
