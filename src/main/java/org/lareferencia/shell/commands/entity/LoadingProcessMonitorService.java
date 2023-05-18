package org.lareferencia.shell.commands.entity;

import org.apache.cxf.management.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Service;

@Service
@ManagedResource(objectName = "org.lareferencia.shell.commands.entity:type=LoadingProcessMonitor")
public class LoadingProcessMonitorService {
	private int loadedEntities;
	private int duplicatedEntities;
	private int discardedEntities;
	private int processedFiles;
	private int errorFiles;

	// Implemente a lógica para recuperar as informações relevantes do processo de
	// carregamento

	@ManagedAttribute(description = "Loading process in progress")
	public Boolean isLoadingProcessInProgress() {
		// Implemente a lógica para verificar se o processo de carregamento está em
		// andamento
		return null;
	}

	@ManagedAttribute(description = "Number of individuals of the loaded entities")
	public int getLoadedEntities() {
		return loadedEntities;
	}

	@ManagedAttribute(description = "Number of individuals of the duplicated entities")
	public int getDuplicatedEntities() {
		return duplicatedEntities;
	}

	@ManagedAttribute(description = "Number of individuals from discarded entities")
	public int getDiscardedEntities() {
		return discardedEntities;
	}

	@ManagedAttribute(description = "Number of successfully processed files")
	public int getProcessedFiles() {
		return processedFiles;
	}

	@ManagedAttribute(description = "Number of files processed with error")
	public int getErrorFiles() {
		return errorFiles;
	}
}
