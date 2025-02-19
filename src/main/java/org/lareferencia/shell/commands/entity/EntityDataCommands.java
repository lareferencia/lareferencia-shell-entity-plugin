
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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


	@ShellMethod("Load entity-relation data from from xml. If path points to a directory all contained .xml files will be loaded, otherwise only referenced file will be loaded ")
    public String load_data(@ShellOption(value = "--path", defaultValue = "false") String path,
                            @ShellOption(value = "--dryRun", defaultValue = "false") String dryRun,
                            @ShellOption(value = "--doProfile", defaultValue = "false") String doProfile,
                            @ShellOption(value = "--threadsToRun", defaultValue = "0") int threadsToRun) throws Exception {

        logger.info("Running in dry-run mode: " + dryRun);

        Boolean dryRunMode = Boolean.parseBoolean(dryRun);
        Profiler generalProfiler = new Profiler(true, "\nPath: " + path + " ").start();

		if (threadsToRun == 0) {
			threadsToRun = Runtime.getRuntime().availableProcessors();
		}

        // Crear un pool de hilos
        ExecutorService executor = Executors.newFixedThreadPool(threadsToRun);

        // Procesar archivos XML
        processFiles(new File(path), dryRunMode, executor);

        // Esperar a que todos los hilos terminen
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            logger.error("Thread execution interrupted", e);
        }

		logger.info( "Running post processing tasks" );
		
		// merge data if not in dry run mode
		if(!dryRunMode) 
			erService.mergeEntityRelationData();

		// set loading process as finished
		entityLoadingMonitorService.setLoadingProcessInProgress(false);

		// write to json
		entityLoadingMonitorService.writeToJSON(path);

		// write report to log
		//logger.info(entityLoadingMonitorService.loadingReport());

		generalProfiler.report(logger);
		return entityLoadingMonitorService.loadingReport();
    }


	private void processFiles(File file, Boolean dryRunMode, ExecutorService executor) {
        if (file.isDirectory()) {
            for (File subFile : file.listFiles()) {
                processFiles(subFile, dryRunMode, executor);
            }
        } else if (file.isFile() && file.getName().endsWith(".xml")) {
            executor.submit(() -> load_xml_file(file, dryRunMode));
        }
    }	

	

	
	
	private void load_xml_file(File file, Boolean dryRun) {
		
		System.out.println("!!!====>>>> file.getAbsolutePath(): "+file.getAbsolutePath());
		
		try {
			InputStream input = new FileInputStream(file);

			// increment total processed files
			entityLoadingMonitorService.incrementTotalProcessedFiles();

			DocumentBuilder dBuilder = threadLocalDocumentBuilder.get();
		
			Document doc = dBuilder.parse(input);
			
			EntityLoadingStats stats = erService.parseAndPersistEntityRelationDataFromXMLDocument(doc, dryRun);
			entityLoadingMonitorService.reportEntityLoadingStats(stats);
			entityLoadingMonitorService.incrementTotalSuccessfulFiles();
		
		} catch (EntitiyRelationXMLLoadingException e) {
			// set file name for error reporting
			e.setFileName(file.getAbsolutePath());
			// report exception 
			entityLoadingMonitorService.reportException(e);

			// the file was not loaded so increment total failed files
			entityLoadingMonitorService.incrementTotalFailedFiles();
		} catch (EntityRelationException e) {
			// TODO report this kind of exception too 
			logger.error( String.format("Undetailed EntityRelation Exception while loading xml data into entity model - %s was not loaded - details: %s", file.getAbsolutePath(), e.getMessage() ) );
			entityLoadingMonitorService.incrementTotalFailedFiles();
		} catch (Exception e) {
			// TODO report this kind of exception too 
			logger.error( String.format("General Exception while loading xml data into entity model - %s was not loaded - details: %s", file.getAbsolutePath(), e.getMessage() ) );
			entityLoadingMonitorService.incrementTotalFailedFiles();
		} 
		
		
		
	}


	
	
}
