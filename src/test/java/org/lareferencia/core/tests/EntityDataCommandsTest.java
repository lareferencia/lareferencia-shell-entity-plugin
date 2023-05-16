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

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.lareferencia.core.util.date.DateHelper;
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
    
	@Autowired
	private DateHelper dateHelper;

    @Test
    public void loadDataOnDryRunMode() throws Exception {
        String XMLFolder = "/Users/jbjares/Documents/entity_data_folder/lattes_di";
        String doProfile = "true";
        String dryRun = "true";
        String result = entityDataCommands.load_data(XMLFolder, doProfile,dryRun);
        logger.info("result ===>>>> "+result);
    }
    
    

}
