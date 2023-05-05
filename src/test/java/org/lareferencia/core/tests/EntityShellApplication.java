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


import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.shell.Shell;
import org.springframework.shell.jline.InteractiveShellApplicationRunner;
import org.springframework.stereotype.Component;


@Component
@Order(InteractiveShellApplicationRunner.PRECEDENCE - 200)
public class EntityShellApplication implements ApplicationRunner {
	
	 private Shell shell;
	 private ConfigurableEnvironment environment;


	public static void main(String[] args) {
		
		 SpringApplication springApplication = 
	                new SpringApplicationBuilder()
	                .sources(EntityShellApplication.class)
	                .web(WebApplicationType.NONE)
	                .build();
		 

	     springApplication.run(args).close();	
	}
	
	
	 public EntityShellApplication(Shell shell, ConfigurableEnvironment environment) {
	        this.shell = shell;
	        this.environment = environment;
	    }
	
	@Override
	public void run(ApplicationArguments args) throws Exception {
		
		  if (args.getNonOptionArgs().isEmpty() || args.getNonOptionArgs().stream().allMatch(s -> s.startsWith("@"))) {
          		System.out.println("!!===> args.getNonOptionArgs.get(0): "+args.getNonOptionArgs().isEmpty());
          		System.out.println("!!===> args.getOptionNames: "+args.getOptionNames().isEmpty());
          		System.out.println("!!===> args.getSourceArgs[0]: "+(args.getSourceArgs().length==0));
            	if(args.getNonOptionArgs().isEmpty() && 
            			args.getNonOptionArgs().isEmpty() && 
            			(args.getSourceArgs().length==0)) {
            		
                    InteractiveShellApplicationRunner.disable(environment);
                    String input = String.join(" ", args.getSourceArgs());
                    shell.evaluate(() -> input);
                    shell.evaluate(() -> "exit");
            	}
	        } else {
	            InteractiveShellApplicationRunner.disable(environment);
	            
	            final Object evaluate = shell.evaluate(() -> String.join(" ", args.getSourceArgs()));
	            if (evaluate != null) {
	            	if(args.getNonOptionArgs().isEmpty() && 
	            			args.getNonOptionArgs().isEmpty() && 
	            			(args.getSourceArgs().length==0)) {
	            		
	                    InteractiveShellApplicationRunner.disable(environment);
	                    String input = String.join(" ", args.getSourceArgs());
	                    shell.evaluate(() -> input);
	                    shell.evaluate(() -> "exit");
	            		
	            	}else {
		                System.out.println(evaluate);
	            	}

	            }
	        }
		
	}

 }