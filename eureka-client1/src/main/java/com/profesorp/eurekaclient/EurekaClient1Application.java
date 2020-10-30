package com.profesorp.eurekaclient;

import java.util.Enumeration;

import javax.annotation.PostConstruct;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.core.env.Environment;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableEurekaClient
@RestController
public class EurekaClient1Application {
	final  static String SALTOLINEA="\n";
	
	@Autowired
	Environment environment;
	
	int puerto;
	
	public static void main(String[] args) {
		SpringApplication.run(EurekaClient1Application.class, args);
	}
	@PostConstruct
	void iniciado()
	{
		puerto=Integer.parseInt(environment.getProperty("server.port"));
	}
	@GetMapping("/")
	public String get1(ServerHttpRequest request)
	{
		return "En get1 de servidor corriendo en puerto: "+puerto+getRequest(request);
	}
	@GetMapping("/dummy")
	public String dummy(ServerHttpRequest request)
	{
		return "En dummy de servidor corriendo en puerto: "+puerto+getRequest(request);
	}
	
	@GetMapping("/dummy/{param1}")
	public String dummyParam(@PathVariable String param1,ServerHttpRequest request)
	{
		return "En dummy con parametro: "+param1+" de servidor corriendo en puerto: "+puerto+getRequest(request);
	}
	public String getRequest(ServerHttpRequest request)
	{
		StringBuffer strLog=new StringBuffer(SALTOLINEA);
		
		strLog.append("Metodo: "+request.getMethod()+SALTOLINEA);
		strLog.append("URL: "+request.getURI()+SALTOLINEA);
		strLog.append("Host Remoto: "+request.getRemoteAddress()+SALTOLINEA);
		strLog.append("----- PARAMETERS ----"+SALTOLINEA);
		request.getQueryParams().forEach( (key,value) ->
		{
			for (int n=0;n<value.size();n++)
			{
				strLog.append("Clave:"+key+ " Valor: "+value.get(n)+SALTOLINEA);
			}
		} );
		
		strLog.append(SALTOLINEA+"----- Headers ----"+SALTOLINEA);
		request.getHeaders().forEach( ( key,valor) ->	
		{
				for (int n=0;n<valor.size();n++)
					strLog.append("Clave:"+key+ " Valor: "+valor.get(n) +SALTOLINEA);
		});
		return strLog.toString();
	}
}
