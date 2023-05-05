package org.lareferencia.core.tests;

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

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.lareferencia.shell.commands.entity.EntityDataCommands;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
@ComponentScan("org.lareferencia.core.entity.services")
@EnableJpaRepositories("org.lareferencia.core.entity.repositories.jpa")
@ImportResource({"classpath*:application-context.xml"})
@EnableAutoConfiguration( exclude = { org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class, ElasticsearchDataAutoConfiguration.class })
public class EntityDataCommandsTest {
	
	private static Logger logger = LogManager.getLogger(EntityDataCommandsTest.class);
	
    @Autowired
    private EntityDataCommands entityDataCommands;

    @Test
    public void testLoadData() throws Exception {
        String XMLFolder = "entity_data_folder";
		Resource resource = new ClassPathResource(XMLFolder);

        Integer entityCacheSize = 5000;
        String doProfile = "true";
        Boolean dryRun = Boolean.TRUE;
        
        logger.info("!!!==>> "+resource.getURL().getPath());
        
		boolean exists =      resource.exists();
		boolean isFile =      resource.isFile();
		assertTrue(exists);
		assertTrue(isFile);
	
		logger.info("======>>>> resource.getURL().getPath():  "+resource.getURL().getPath());
        String result = entityDataCommands.load_data(resource.getURL().getPath(), entityCacheSize, doProfile, dryRun);
        logger.info("result ===>>>> "+result);
    }
}
