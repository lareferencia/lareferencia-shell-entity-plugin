
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

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.core.entity.services.EntityDataService;
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
	
//	@Autowired
//	EntityLRUCache entityCache;

	/**
	@ShellMethod("Load entity-relation data from  from xml. If path points to a directory all contained .xml files will be loaded, otherwise only referenced file will be loaded ")
	public String load_data(String path, @ShellOption(defaultValue = "1000") Integer entityCacheSize, @ShellOption(defaultValue = "false") String doProfile) throws Exception {
			
		Profiler generalProfiler = new Profiler(true, "\nPath: " + path + " ").start();
		
		// cache setting
//		if ( entityCacheSize != null && entityCacheSize > 0) {
//			logger.info("Creating entity cache ... size: " + entityCacheSize );	
//			entityCache.setCapacity(entityCacheSize);
//			erService.setEntityCache(entityCache);
//		}

		Boolean profileMode = Boolean.valueOf(doProfile);		
		File fileOrDirectory = new File(path);
	
		boolean exists =      fileOrDirectory.exists();      // Check if the file exists
		boolean isFile =      fileOrDirectory.isFile();      // Check if it's a regular file
	
		if ( !exists ) {
			
			logger.error( String.format("%s does not exists.", path ) );
			throw new Exception("Path: " + path + "doesn exists.");
		
		} else if ( isFile ) {
			
			logger.info( String.format("Processing path: %s", path ) );
			load_xml_file(fileOrDirectory, profileMode);
		
		} else { // is a directory
			
			load_directory(fileOrDirectory.getAbsolutePath(), profileMode);
		}
			
		
//		if ( entityCacheSize != null && entityCacheSize > 0) {
//			logger.info("Persisting entity cache ...");	
//			entityCache.syncAndClose();
//		}
		
	
		
		logger.info( "Running post processing tasks" );
		erService.mergeEntityRelationData();
		
		generalProfiler.report(logger);
		return String.format("Processing %s finished \n\n",path);
		
	}
	**/
	
	@ShellMethod("Load entity-relation data from from xml. If path points to a directory all contained .xml files will be loaded, otherwise only referenced file will be loaded ")
	public String load_data(@ShellOption(value="path", defaultValue = "false")String path, @ShellOption(value="dryRun", defaultValue = "false") String dryRun, @ShellOption(value = "doProfile", defaultValue = "false") String doProfile) throws Exception {

	    logger.info("Running in dry-run mode: "+dryRun+". No changes will be made.");
	    
	    Boolean dryRunMode = Boolean.parseBoolean(dryRun);
	    Profiler generalProfiler = new Profiler(true, "\nPath: " + path + " ").start();

	     
		Boolean profileMode = Boolean.valueOf(doProfile);		
		File fileOrDirectory = new File(path);
	
		boolean exists =      fileOrDirectory.exists();      // Check if the file exists
		boolean isFile =      fileOrDirectory.isFile();      // Check if it's a regular file
	
		if ( !exists ) {
			
			logger.error( String.format("%s does not exists.", path ) );
			throw new Exception("Path: " + path + "doesn exists.");
		
		} else if ( isFile ) {	
			logger.info( String.format("Processing path: %s", path ) );
			load_xml_file(fileOrDirectory, profileMode,dryRunMode);
			
		} else { // is a directory
			load_directory(fileOrDirectory.getAbsolutePath(), profileMode,dryRunMode);
		}
		
		logger.info( "Running post processing tasks" );
		
		if(!dryRunMode) {
			erService.mergeEntityRelationData();
		}
		erService.getEntityLoadingMonitorService().generateSummaryofTotalProcessedFiles(path);
		erService.getEntityLoadingMonitorService().resetAllCachedData();

		
		generalProfiler.report(logger);
		return String.format("Processing %s finished \n\n",path);

	}

	
	private void load_directory(String path, Boolean profileMode, Boolean dryRun) {
		
		File fileOrDirectory = new File(path);
		
		for ( final File fileEntry : fileOrDirectory.listFiles() ) {
	        
			if ( !fileEntry.isDirectory()  )  {
	        	
	        	if ( fileEntry.getName().endsWith(".xml") ) {
	    			logger.info( String.format("Processing file: %s", fileEntry.getAbsolutePath() ) );
	        		load_xml_file(fileEntry, profileMode,dryRun);
	        	}
	        	
	        } else {
	        	logger.info("Entering directory: " + fileEntry.getAbsolutePath() );
	        	load_directory( fileEntry.getAbsolutePath(), profileMode,dryRun);
	        }
		}
	}
	
	
	private void load_xml_file(File file, Boolean profileMode, Boolean dryRun) {
		
		System.out.println("!!!====>>>> file.getAbsolutePath(): "+file.getAbsolutePath());
		profiler = new Profiler(profileMode, "File: " + file.getAbsolutePath() + " ").start();
		erService.setProfiler(profiler);
		
		try {
			InputStream input = new FileInputStream(file);
		
			Document doc = dBuilder.parse(input);
			profiler.messure("XMLParse");
			
			if(dryRun) {
				profiler.messure("dry-run mode on");
				erService.validateXMLEntityModelParseBeforePersist(doc,file.getAbsolutePath());
			}else {	
				profiler.messure("dry-run mode off");
				erService.parseAndPersistEntityRelationDataFromXMLDocument(doc);
			}

			profiler.report(logger);
		
		} catch (Exception e) {
			logger.error( String.format("Error loading xml data into entity model - %s was not loaded - details: %s", file.getAbsolutePath(), e.getMessage() ) );
		} 
		
		
		
	}


	
	
}
