package org.lareferencia.core.tests;

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
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles("test")
@SpringBootTest
@ComponentScan("org.lareferencia.core.entity.services")
@EnableJpaRepositories("org.lareferencia.core.entity.repositories.jpa")
@ImportResource({"classpath*:application-context.xml"})
@TestPropertySource(properties = "spring.jmx.enabled=true")
@EnableAutoConfiguration( exclude = { org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class, ElasticsearchDataAutoConfiguration.class })
public class EntityDataCommandsTest {
	
	private static Logger logger = LogManager.getLogger(EntityDataCommandsTest.class);
	
    @Autowired
    private EntityDataCommands entityDataCommands;
    


    @Test
    public void loadDataOnDryRunMode() throws Exception {
        String XMLFolder = "/Users/jbjares/Documents/entity_data_folder/lattes_di";
        String doProfile = "true";
        String dryRun = "false";
        String result = entityDataCommands.load_data(XMLFolder, doProfile,dryRun);
        logger.info("result ===>>>> "+result);
    }
    
    

}
