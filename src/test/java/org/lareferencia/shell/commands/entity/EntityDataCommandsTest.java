package org.lareferencia.shell.commands.entity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;


@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = { EntityConfiguration.class })
public class EntityDataCommandsTest {
	
    @Autowired
    private EntityDataCommands entityDataCommands;

    @Test
    public void testLoadData() throws Exception {
        String path = "path/to/file.xml";
        Integer entityCacheSize = 5000;
        String doProfile = "true";
        Boolean dryRun = Boolean.TRUE;
        String result = entityDataCommands.load_data(path, entityCacheSize, doProfile,dryRun);
    }
}
