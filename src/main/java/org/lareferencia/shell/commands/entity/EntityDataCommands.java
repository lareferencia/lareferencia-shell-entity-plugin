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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.core.entity.domain.EntityRelationException;
import org.lareferencia.core.entity.services.EntityDataService;
import org.lareferencia.core.entity.services.EntityLoadingMonitorService;
import org.lareferencia.core.entity.services.EntityLoadingStats;
import org.lareferencia.core.entity.services.exception.EntitiyRelationXMLLoadingException;
import org.lareferencia.core.util.Profiler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.w3c.dom.Document;


@ShellComponent
public class EntityDataCommands {
	
	private static Logger logger = LogManager.getLogger(EntityDataCommands.class);
	
	static javax.xml.parsers.DocumentBuilder dBuilder;
		
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
	
	
}
