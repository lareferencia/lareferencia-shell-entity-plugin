package org.lareferencia.shell.commands.entity;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LoadingProcessMonitorServiceTest {
    
    private LoadingProcessMonitorService monitorService;
    
    @BeforeEach
    public void setup() {
        monitorService = new LoadingProcessMonitorService();
        // Configuração adicional para os testes, se necessário
    }
    
    @Test
    public void testIsLoadingProcessInProgress() {
        boolean loadingInProgress = monitorService.isLoadingProcessInProgress();
        Assertions.assertTrue(loadingInProgress, "Expected loading process to be in progress");
    }
    
    @Test
    public void testGetLoadedEntities() {
        int loadedEntities = monitorService.getLoadedEntities();
        Assertions.assertEquals(10, loadedEntities, "Expected 10 loaded entities");
    }
    
    // Adicione outros métodos de teste para testar outros atributos e métodos do serviço
    
}
