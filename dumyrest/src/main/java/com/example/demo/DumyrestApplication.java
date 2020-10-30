package com.example.demo;

import java.util.Enumeration;

import javax.servlet.http.HttpServletRequest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class DumyrestApplication {
	final  static String SALTOLINEA="\n";
	
	public static void main(String[] args) {
		SpringApplication.run(DumyrestApplication.class, args);
	}
	@RequestMapping("/")
	public String get1(HttpServletRequest request)
	{
		return "En get1 de DummyRest"+getRequest(request);
	}
	@GetMapping("/dummy")
	public String dummy(HttpServletRequest request)
	{
		return "En dummy de DummyRest"+getRequest(request);
	}
	
	@GetMapping("/dummy/{param1}")
	public String dummyParam(@PathVariable String param1,HttpServletRequest request)
	{
		return "En dummy con parametro "+param1+ " de DummyRest"+getRequest(request);
	}
	@RequestMapping("/custom")
	public String getCustom(HttpServletRequest request)
	{
		return "En /custom/ "+DumyrestApplication.getRequest(request);
	}
	public static String getRequest(HttpServletRequest request)
	{
		StringBuffer strLog=new StringBuffer(SALTOLINEA);
		
		strLog.append("Metodo: "+request.getMethod()+SALTOLINEA);
		strLog.append("URL: "+request.getRequestURL()+SALTOLINEA);
		strLog.append("Host Remoto: "+request.getRemoteHost()+SALTOLINEA);
		strLog.append("----- PARAMETERS ----"+SALTOLINEA);
		request.getParameterMap().forEach( (key,value) ->
		{
			for (int n=0;n<value.length;n++)
			{
				strLog.append("Clave:"+key+ " Valor: "+value[n]+SALTOLINEA);
			}
		} );
		
		strLog.append(SALTOLINEA+"----- Headers ----"+SALTOLINEA);
		Enumeration<String> nameHeaders=request.getHeaderNames();				
		while (nameHeaders.hasMoreElements())
		{
			String name=nameHeaders.nextElement();
			Enumeration<String> valueHeaders=request.getHeaders(name);
			while (valueHeaders.hasMoreElements())
			{
				String value=valueHeaders.nextElement();
				strLog.append("Clave:"+name+ " Valor: "+value+SALTOLINEA);
			}
		}
		return strLog.toString();
	}
}
