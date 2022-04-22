
/*
 * Copyright (c) 2013-2022. LA Referencia / Red CLARA and others
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * This file is part of LA Referencia software platform LRHarvester v4.x
 * For any further information please contact Lautaro Matas <lmatas@gmail.com>
 *
 */
package org.lareferencia.shell.commands.entity;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.lareferencia.backend.taskmanager.TaskManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.ClientConfiguration.MaybeSecureClientConfigurationBuilder;
import org.springframework.data.elasticsearch.client.RestClients;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.solr.core.SolrTemplate;
import org.springframework.data.solr.repository.config.EnableSolrRepositories;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;


@Configuration
@EnableSolrRepositories( basePackages = "org.lareferencia.core.entity.repositories.solr"/*, namedQueriesLocation = "classpath:solr-named-queries.properties", */)
public class SolrConfiguration  {
	
	
	/** Configurations beans for solr services */
    @Bean
    public SolrTemplate shellSolrTemplate(SolrClient client) throws Exception {
        return new SolrTemplate(client);
    }
 }
