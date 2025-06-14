
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

import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.util.Arrays;

import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.tdb.TDBFactory;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.core.entity.domain.EntityRelationException;
import org.lareferencia.core.entity.domain.EntityType;
import org.lareferencia.core.entity.indexing.filters.IFieldOccurrenceFilter;
import org.lareferencia.core.entity.indexing.service.IEntityIndexer;
import org.lareferencia.core.entity.indexing.solr.EntityIndexerSolrImpl;
import org.lareferencia.core.entity.repositories.jpa.EntityRepository;
import org.lareferencia.core.entity.repositories.solr.EntitySolrRepository;
import org.lareferencia.core.entity.services.EntityDataService;
import org.lareferencia.core.entity.services.EntityLoadingMonitorService;
import org.lareferencia.core.entity.workers.EntityIndexingWorker;
import org.lareferencia.core.entity.workers.EntityIndexingRunningContext;
import org.lareferencia.core.worker.IWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.tdb.TDBFactory;
import org.apache.jena.sparql.core.DatasetGraph;




@ShellComponent
@Configuration
public class EntityIndexingCommands {
    private static Logger logger = LogManager.getLogger(EntityIndexingCommands.class);
	
	@Autowired
	EntitySolrRepository entitySolrRepository;
	
	@Autowired
	EntityDataService erService;

	@Autowired
	EntityLoadingMonitorService entityMonitorService;
	
	@Autowired
	EntityRepository entityRepository;
	
	@Autowired
	ApplicationContext applicationContext;
	
	@ShellMethod("List all indexers currently available")
	public void listIndexers()  {
			
		for (String beanName : applicationContext.getBeanNamesForType(IEntityIndexer.class) ) {
			System.out.println(beanName);
		}
		
	}
	
	@ShellMethod("List all indexing filters beans currently available (note: not the filter name to be used in indexing config)")
	public void listIndexingFilters()  {
			
		for (String beanName : applicationContext.getBeanNamesForType(IFieldOccurrenceFilter.class) ) {
			System.out.println(beanName);
		}
		
	}

	@ShellMethod("Tranform Jenna tdb binary files to xml") 
	public String transformJenaTDBToXML(@ShellOption(value = "--path") String path ) throws Exception {

		Dataset dataset = TDB2Factory.connectDataset(path);
        
        dataset.begin(ReadWrite.READ);
        try {
            DatasetGraph dsg = dataset.asDatasetGraph();

            RDFDataMgr.write(new FileOutputStream( path + ".xml" ), dsg.getDefaultGraph(), RDFFormat.RDFXML);
		} catch (Exception e) {
			logger.error("Error transforming Jena TDB to XML: " + e.getMessage(), e);
			return "Error transforming Jena TDB to XML: " + e.getMessage();

        } finally {
            dataset.end();
        }

		return "Jena TDB files transformed to XML and saved in: " + path + ".xml";
    }



	@ShellMethod("Index entities of entityTypeName (optional) in indexerName indexing using a given configFile, lastUpdate (yyyy-MM-ddTHH:mm:ss) and provenance are optional")
	public String indexEntities(String configFileFullPath, String indexerName,  @ShellOption(defaultValue = "null") String entityTypeName, @ShellOption(defaultValue="null") String provenance, @ShellOption(defaultValue="null") String lastUpdate, 
		@ShellOption(defaultValue="1000") Integer pageSize, @ShellOption(defaultValue="1") Integer fromPage,  @ShellOption(defaultValue="0") Integer threadsToRun) throws EntityRelationException {

		
		EntityType entityType = null;
		
        if (indexerName != null && !isValidBeanName(indexerName)) {
            String indexersFlatList = String.join(", ", applicationContext.getBeanNamesForType(IEntityIndexer.class));
            logger.error("Invalid indexer name: " + indexerName + ", valid ones are: " + indexersFlatList);
        } else {

			IWorker<EntityIndexingRunningContext> worker = null;

			try {

				if (entityTypeName != null && !entityTypeName.trim().equals("") && !entityTypeName.equals("null"))
					entityType = erService.getEntityTypeFromName(entityTypeName.trim());

				// reset monitor
				entityMonitorService.reset();

				worker = (IWorker<EntityIndexingRunningContext>) applicationContext.getBean(EntityIndexingWorker.class);

				EntityIndexingRunningContext runningContext = new EntityIndexingRunningContext(configFileFullPath, indexerName);
				runningContext.setDeleteMode(false);
				runningContext.setEntityType(entityType);
				runningContext.setPageSize(pageSize);
				runningContext.setFromPage(fromPage);
				runningContext.setProcessToRun(threadsToRun);

				if ( !provenance.equals("null") ) {

					runningContext.setProvenanceSource(provenance);
					System.out.println("Indexing entities from provenance source: " + provenance + "lastUpdate will be ignored");

				} else if ( !lastUpdate.equals("null") ) {
					// parse last update into localdatetime

					try {
						LocalDateTime lastUpdateDate = LocalDateTime.parse(lastUpdate);
						runningContext.setLastUdate(lastUpdateDate);
						System.out.println("Indexing entities from last update: " + runningContext.getLastUdate());
					} catch (Exception e) {
						logger.error("Error parsing last update date. Please use ISO format: yyyy-MM-ddTHH:mm:ss");
						return "Error parsing last update date. Please use ISO format: yyyy-MM-ddTHH:mm:ss";
					}

				}

				worker.setRunningContext(runningContext);

				worker.run();

				// write to JSON
				entityMonitorService.writeToJSON(indexerName);

				worker.stop();

			} catch (Exception e) {
				System.out.println("Error running indexing process. ) "  + e.getMessage() );
				e.printStackTrace();

				if (worker != null) worker.stop();
			}
        }

		return entityMonitorService.indexingReport();
	}
	

    @ShellMethod("Index entities of entityTypeName in Solr using a given configFile")
    public void indexEntitiesSolr(String configFileFullPath, @ShellOption() String entityTypeName, @ShellOption(defaultValue="none") String provenance,
            @ShellOption(defaultValue = "1000") Integer pageSize) throws EntityRelationException {
        String indexerName = "entityIndexerSolr";
        indexEntities(configFileFullPath, indexerName, entityTypeName, provenance, "null", pageSize, 1,0);
    }

	@Bean
    @Scope("prototype")
    public EntityIndexingWorker entityRelationIndexerWorker() {
    	return new EntityIndexingWorker();
    }
	
	@Bean
    @Scope("prototype")
    public IEntityIndexer entityIndexerSolr () {
    	return new EntityIndexerSolrImpl();
    }
	

    private boolean isValidBeanName(String testBeanName) {
        return Arrays.stream(applicationContext.getBeanNamesForType(IEntityIndexer.class))
                .anyMatch(testBeanName::equals);
    }
}
