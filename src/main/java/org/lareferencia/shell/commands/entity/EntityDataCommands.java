
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
	
	private Profiler profiler = new Profiler(false, "");
	
	static {
	
		try {
		dBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		} catch (Exception e) { // TODO: log this
			System.out.println("Exception creating document builder");
		}
	
	}
	
	@Autowired
	EntityDataService erService;

	@Autowired
	private EntityLoadingMonitorService entityLoadingMonitorService;

	@ShellMethod("Load entity-relation data from from xml. If path points to a directory all contained .xml files will be loaded, otherwise only referenced file will be loaded ")
	public String load_data(@ShellOption(value="path", defaultValue = "false")String path, @ShellOption(value="dryRun", defaultValue = "false") String dryRun, @ShellOption(value = "doProfile", defaultValue = "false") String doProfile, @ShellOption(value = "threadsToRun", defaultValue = "1") int threadsToRun) throws Exception {

		logger.info("Running in dry-run mode: "+dryRun+". No changes will be made.");
		
		Boolean dryRunMode = Boolean.parseBoolean(dryRun);
		Profiler generalProfiler = new Profiler(true, "\nPath: " + path + " ").start();

		Boolean profileMode = Boolean.valueOf(doProfile);		
		File fileOrDirectory = new File(path);

		entityLoadingMonitorService.setLoadingProcessInProgress(true);

		boolean exists =      fileOrDirectory.exists();      // Check if the file exists
		boolean isFile =      fileOrDirectory.isFile();      // Check if it's a regular file
	
		if ( !exists ) {
			logger.error( String.format("%s does not exists.", path ) );
		} else if ( isFile ) {	
			logger.info( String.format("Processing path: %s", path ) );
			load_xml_file(fileOrDirectory, profileMode,dryRunMode);
			
		} else { // is a directory
			if (threadsToRun == 0) {
				threadsToRun = Runtime.getRuntime().availableProcessors();
			}
			load_directory(fileOrDirectory.getAbsolutePath(), profileMode, dryRunMode, threadsToRun);
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

	
	private void load_directory(String path, Boolean profileMode, Boolean dryRun, int threadsToRun) {
		
		File fileOrDirectory = new File(path);
		File[] files = fileOrDirectory.listFiles();
		
		if (files == null) {
			logger.error("No files found in directory: " + path);
			return;
		}

		ExecutorService executor = Executors.newFixedThreadPool(threadsToRun);
		
		for (final File fileEntry : files) {
			executor.submit(() -> {
				if (!fileEntry.isDirectory()) {
					if (fileEntry.getName().endsWith(".xml")) {
						logger.info(String.format("Processing file: %s", fileEntry.getAbsolutePath()));
						load_xml_file(fileEntry, profileMode, dryRun);
					}
				} else {
					logger.info("Entering directory: " + fileEntry.getAbsolutePath());
					load_directory(fileEntry.getAbsolutePath(), profileMode, dryRun, threadsToRun);
				}
			});
		}
		
		executor.shutdown();
		try {
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			logger.error("Thread execution interrupted", e);
		}
	}
	
	
	private void load_xml_file(File file, Boolean profileMode, Boolean dryRun) {
		
		//System.out.println("!!!====>>>> file.getAbsolutePath(): "+file.getAbsolutePath());
		profiler = new Profiler(profileMode, "File: " + file.getAbsolutePath() + " ").start();
		erService.setProfiler(profiler);
		
		try {
			InputStream input = new FileInputStream(file);

			// increment total processed files
			entityLoadingMonitorService.incrementTotalProcessedFiles();
		
			Document doc = dBuilder.parse(input);
			profiler.messure("XMLParse");
			
			profiler.messure("dry-run:" + dryRun);
			EntityLoadingStats stats = erService.parseAndPersistEntityRelationDataFromXMLDocument(doc, dryRun);
			entityLoadingMonitorService.reportEntityLoadingStats(stats);
			profiler.messure("parseAndPersistEntityRelationDataFromXMLDocument");
			profiler.report(logger);

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
