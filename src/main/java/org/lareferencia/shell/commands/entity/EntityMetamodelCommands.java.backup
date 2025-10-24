
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
import java.nio.charset.Charset;

import org.apache.commons.io.FileUtils;
import org.lareferencia.core.entity.services.EntityMetamodelService;
import org.lareferencia.core.entity.xml.XMLEntityRelationMetamodel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;


@ShellComponent
public class EntityMetamodelCommands {
	
	@Autowired
	EntityMetamodelService erManager;
	
	
	@ShellMethod("Load metamodel from xml")
	public String load_model(String filename) throws Exception {
		
		File file = new File(filename);
		
		String contents = FileUtils.readFileToString(file, Charset.forName("UTF-8"));
		
		XMLEntityRelationMetamodel config = erManager.loadConfigFromXml(contents);
		
		erManager.persist(config);
		
		return String.format("%s was loaded.", filename);
	}
	
	@ShellMethod("Export metamodel to xml")
	public String save_model(String filename) throws Exception {
		
		File file = new File(filename);
			
		XMLEntityRelationMetamodel config = erManager.getConfigFromDB();
		
		String contents = erManager.saveConfigToXml(config);
		
		FileUtils.writeStringToFile(file, contents, Charset.forName("UTF-8"));
		
		return String.format("Metamodel was saved to %s ", filename);
	}
	
}
