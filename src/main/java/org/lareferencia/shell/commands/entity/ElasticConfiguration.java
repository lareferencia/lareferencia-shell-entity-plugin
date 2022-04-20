
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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;


@Configuration
//@EnableElasticsearchRepositories( basePackages = "org.lareferencia.core.entity.repositories.elastic")
public class ElasticConfiguration  {
	
	
	
    /****** elastic */
    
    @Value("${elastic.host:localhost}")
    private String host;

    @Value("${elastic.port:9200}")
    private int port;

    @Value("${elastic.username:user}")
    private String username;

    @Value("${elastic.password:password}")
    private String password;

//    @Bean
//    public RestHighLevelClient elasticsearchClient() {
//    	
//    	 ClientConfiguration.MaybeSecureClientConfigurationBuilder builder = (MaybeSecureClientConfigurationBuilder) ClientConfiguration.builder()
//                 .connectedTo(host+ ":" + port);
//                 //.usingSsl() 
//                 //.withBasicAuth(username, password); 
//    	 
//         final ClientConfiguration clientConfiguration = builder.build();
//         return RestClients.create(clientConfiguration).rest();
//
//    }
//    
//   
// 
//    @Bean
//    public ElasticsearchOperations elasticsearchTemplate() {
//        return new ElasticsearchRestTemplate(elasticsearchClient());
//    }
    

    
 }
