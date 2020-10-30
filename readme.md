---
title: Spring Cloud Gateway. Redirigiendo Reactivamente.
pre: "<b>o </b>"
author: El Profe
type: post
date: 2020-10-28T17:00:00+00:00
url: spring/gateway
categories:
  - cloud
  - java
  - json
  - rest
  - seguridad
  - spring boot
  - gateway
tags:
  - cloud
  - gateway
  - hystrix
  - java
  - spring boot
  - spring cloud gateway

---
[Hace tiempo escribí un articulo](http://www.profesor-p.com/2019/03/16/zuul-para-redirigir-peticiones-rest-en-spring-boot/) sobre como realizar una pasarela o *gateway* para redirigir peticiones utilizando  **Zuul**. Sin embargo, **Zuul**  ya no esta aconsejado por la gente de *Pivotal*, que como sabréis es la empresa detrás de Spring. Para sustituirlo han creado [Spring Cloud Gateway](https://docs.spring.io/spring-cloud-gateway/docs/2.2.4.RELEASE/reference/html/).

Principalmente, la mayor ventaja de este este software es que es reactivo. Es decir, utiliza las nuevas librerías de [*Webflux* de Spring](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html) para que las llamadas no sean bloqueantes. Esto es una parte muy importante del "Spring 5" y realmente cambia el motor interno de Spring. Sin embargo, el programador podrá seguir utilizando las antiguas características de Spring, pudiendo hacer uso de las nuevas.



En casi cualquier proyecto donde haya microservicios, es deseable que todas las comunicaciones entre esos microservicios pasen por un lugar común, de tal manera que se registren las entradas y salidas, se pueda implementar seguridad o se puedan redirigir las peticiones dependiendo de diversos parámetros.

Con **Spring Cloud Gateway** esto es muy fácil de implementar ya que esta perfectamente integrado con _Spring Boot_,  y sus diferentes funcionalidades.

Como siempre [en mi página de GitHub][3] podéis ver los fuentes sobre los que esta basado este articulo.

### Creando el proyecto.

Para demostrar varias de las capacidades de  **Spring Cloud Gateway** vamos a simular una aplicación donde habrá 2 servicios, uno de  los cuales tendrán dos instancias las cuales se registraran usando **Eureka Server**. 

Nuestro proyecto, por lo tanto, quedara así:

- Servidor Eureka (eureka). Corriendo en puerto 8761.
- Simple Server. (dummyrest). Escuchando en el puerto 8000
- Cliente Eureka (eureka-client1). Corriendo en puerto 8100 y puerto 8101
- Gateway. Corriendo en puerto 8080.

Por no hacer demasiado largo el articulo, no voy a explicar como configurar el servidor y los clientes de Eureka. Tenéis un articulo explicando como hacerlo [en este mismo blog](http://www.profesor-p.com/2019/01/03/microservicios-distribuidos-con-eureka/). Ambos programas son muy simples y viendo el código seguro que no necesitáis más explicaciones ;-) .



 En cuanto a **dummyRest** es una aplicación que simplemente responde en los mismos endpoints que el cliente de eureka, mostrando las características de la petición:

```java
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

```

Como se puede ver, tiene los siguientes endpoints: **/**, **/dummy/**, **/dummy/{param1}**  y **/custom**

El servidor eureakaclient1, es un servidor tipo webflux, que se registra como cliente en un servidor Eureka. El código es el siguiente:

````java
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
	public String dummyParam(ServerHttpRequest request)
	{
		return "En dummy de servidor corriendo en puerto: "+puerto+getRequest(request);
	}
	
	@GetMapping("/dummy/{param1}")
	public String dummy(@PathVariable String param1,ServerHttpRequest request)
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

````

Este servidor responderá en la siguientes rutas: **/**, **/dummy/** y **/dummy/{param1}** 

Ahora vamos a a empezar a hablar directamente del **Gateway**, que es de lo que va este articulo.

Si tenemos instalado _Eclipse_ con el [plugin de _Spring Boot_][4] (lo cual recomiendo), el crear el proyecto seria tan fácil como añadir un nuevo proyecto del tipo _Spring Boot_ incluyendo el _starter_ **Spring Cloud Gateway**. 



![dependencias](.\dependencias.png)

Para poder hacer algunas pruebas también incluiremos el _starter_ **Hystrix** y el **cliente de Eureka.**

También tenemos la opción de crear un proyecto Maven desde la página web <a class="url" href="https://start.spring.io/" target="_blank" rel="noopener noreferrer">https://start.spring.io/</a> que luego importaremos desde nuestro IDE preferido.

### Empezando

Spring Cloud Gateway tiene [una excelente documentación](https://cloud.spring.io/spring-cloud-gateway/reference/html/)  donde explica todas las diferentes opciones, que son muchas, tenemos para realizar las redirecciones. Básicamente una ruta tiene siempre 3 partes.

- **id:** Nombre de la ruta.  Pondremos el nombre que deseemos.
- **uri**. Es el servidor a donde se redirija la petición. Tener en cuenta que es sola dirección sin path. Es decir aquí no podemos poner http://localhost:8080/mipath/ . Bueno, de hecho si que podemos ponerlo, pero solo nos cogerá el protocolo, host y el puerto. Es decir: http://localhost:8080
- **predicates**:  Pondremos las condiciones que debe cumplir la petición para que vaya a la **uri**  que hemos especificado. Esas condiciones pueden ser de diferente tipo como veremos más adelante.
- **filters**: Esto es opcional y permite incluir filtros con los cuales podremos, por ejemplo, añadir y/o borrar cabeceras, redirigir a otras rutas, etc.

 ### Nuestro Proyecto

Inicialmente la única clase que tendrá nuestro proyecto será esta:

```java
@SpringBootApplication
@EnableEurekaClient
public class GatewayApplication {
	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}
}
```

Simple, ¿ verdad ? 

Y ahora viene la diversión, pues vamos a configurar como y a donde debe redirigir las peticiones. Para ello crearemos  el fichero de propiedades, **application.yml** (dentro del directorio *resources*), de esta manera. 

```java
eureka:
  client:
    serviceUrl:
      defaultZone:  http://localhost:8761/eureka  
                 
spring:
  application:
    name:  gateway  
  cloud:
    gateway:
      default-filters:
      - AddResponseHeader=X-Gateway, true
      routes:
      - id: simple
        uri: http://localhost:8000/
        predicates:
        - Path=/simple/{segment}
        filters:
        - SetPath=/dummy/{segment}
      - id: eureka-client
        uri: lb://eureka-client1
        predicates:
        - Path=/eureka/**
        filters:
        - RewritePath=/eureka/(?<segment>.*), /$\{segment}
      - id: host_route 
        uri: http://localhost:8000
        filters:
        - AddRequestHeader=X-Request-PROFE, Hola
        - PrefixPath=/dummy
        - RemoveRequestHeader=X-Request-Foo
        predicates:
        - Host=www.dummy.com,www.simple.com  
        
        

```

Al principio de este fichero, especificamos donde debe buscar el servidor de eureka para registrarse (*eureka.client. serviceUrl.defaultZone*) Además utilizara el servidor para localizar donde estan ubicados los servidores del tipo **eureka-client1** como veremos más adelante.

Con las líneas *cloud.gateway.default-filters* especificamos que en todas las peticiones de vuelta se debe incluir la cabecera **X-Gateway**, con el valor "**true**". En los ejemplos siguientes veremos como se incluye en las respuestas esa cabecera.

Ahora veremos que hacen las líneas:

```
- id: simple
  uri: http://localhost:8000/
  predicates:
  - Path=/simple/{segment}
  filters:
  - SetPath=/dummy/{segment}
```

Como hemos especificado antes, con **id** le ponemos un nombre a la ruta. **uri** especifica que las peticiones serán redirigidas a http://localhost:8000 (donde estará corriendo nuestra aplicación dummyrest). En el apartado **predicates** añadimos la clausula **path** con lo cual esta ruta se cumplira siempre y cuando la petición sea realizada a **/simple/{ALGO}**. Por fin, en la parte **filters** especificamos que se debe cambiar la ruta a **/dummy/{ALGO}** donde "{ALGO}", será la ruta existente después de /simple.  

Vamos a poner un ejemplo, para explicarlo mejor:

Ejecutando el comando 

```
> curl -s -D - http://localhost:8080/simple/PARAMETRO1
```

obtendremos la siguiente salida:

```
HTTP/1.1 200 OK
X-Gateway: true
Content-Type: text/plain;charset=UTF-8
Content-Length: 518
Date: Fri, 30 Oct 2020 07:46:33 GMT

En dummy con parametro a de DummyRest
Metodo: GET
URL: http://localhost:8000/dummy/PARAMETRO1
Host Remoto: 127.0.0.1
----- PARAMETERS ----

----- Headers ----
Clave:user-agent Valor: curl/7.55.1
Clave:accept Valor: */*
Clave:forwarded Valor: proto=http;host="localhost:8080";for="0:0:0:0:0:0:0:1:55964"
Clave:x-forwarded-for Valor: 0:0:0:0:0:0:0:1
Clave:x-forwarded-proto Valor: http
Clave:x-forwarded-port Valor: 8080
Clave:x-forwarded-host Valor: localhost:8080
Clave:host Valor: localhost:8000
Clave:content-length Valor: 0
```

Se puede observar que la petición a sido redirigida a nuestro servicio dummyrest y ha entrado por la función **dummyParam**.

Observar que además en la respuesta tenemos la cabecera **X-gateway** con el valor de true, como le indicamos con el filtro por defecto.

Con las líneas:

```
- id: eureka-client
  uri: lb://eureka-client1
  predicates:
  - Path=/eureka/**
  filters:
  - RewritePath=/eureka/(?<segment>.*), /$\{segment}
```

redirigiremos todo lo que vaya al path **/eureka/**  a nuestros servicios registrados en eureka.  Lo más importante aquí es que la **uri** esta precedida por el protocolo **lb:** lo cual hará que busque en el servidor de eureka por los servicios con el nombre escrito a continuación y las peticiones serán realizadas a esos programas, teniendo en cuenta que además balanceara las peticiones si es necesario.

En el filtro hemos añadido una regla de reescritura para sustituir **/eureka** por **/** 

```java
> curl -s http://localhost:8080/eureka/
...
En get1 de servidor corriendo en puerto: 8101
...
> curl -s http://localhost:8080/eureka/
...
En get1 de servidor corriendo en puerto: 8100
...
```

Os recuerdo que para lanzar el cliente de eureka en diferentes puertos podremos añadir la propiedad **server.port** en las propiedades del sistema al lanzar el programa, por ejemplo de esta manera.

``` 
java -Dserver.port=8081 -jar eurekaclient1.jar
```

En el ultimo ejemplo cogeremos las peticiones mandadas al host www.dummy.com o www.simple.com y las redirigiremos a http://localhost:8000 .

Además, a través de los filtros, pondremos estas condiciones:

- Añadiremos un cabecera en la petición (X-Request-PROFE),
- Si viene la cabecera X-Request-Foo será quitada.
-  Añadiremos  **/dummy/** al path. 

```
 - id: host_route 
   uri: http://localhost:8000
   filters:
   - AddRequestHeader=X-Request-PROFE, Hola
   - PrefixPath=/dummy
   - RemoveRequestHeader=X-Request-Foo
   predicates:
   - Host=www.dummy.com,www.simple.com  
```

Un ejemplo sería esta petición.

```
>curl -s -D - -H "host:www.dummy.com" http://localhost:8080/
HTTP/1.1 200 OK
X-Gateway: true

En dummy de DummyRest
Metodo: GET
URL: http://localhost:8000/dummy/
Host Remoto: 127.0.0.1
----- PARAMETERS ----

----- Headers ----
...
Clave:x-forwarded-host Valor: www.dummy.com
Clave:host Valor: localhost:8000

```










Con ellas especificaremos que todo lo que vaya a la ruta **/google/** y algo más (**) sea redirigido a **<a class="url" href="https://www.google.com/" target="_blank" rel="noopener noreferrer">https://www.google.com/</a>** , teniendo en cuenta que si por ejemplo la petición es realizada `http://localhost:8080/google/search?q=profesor_p` esta será redirigida a `https://www.google.com/search?q=profesor_p`. Es decir lo que añadamos después de **/google/** será incluido en la redirección, debido a los dos asteriscos añadidos al final del path.

Para que el programa funcione solo será necesario añadir la anotación `@EnableZuulProxy`en la clase de inicio, en este caso en: **ZuulSpringTestApplication**

```
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
@SpringBootApplication
@EnableZuulProxy
public class ZuulSpringTestApplication {
	public static void main(String[] args) {
		SpringApplication.run(ZuulSpringTestApplication.class, args);
	}
}
```

Para poder demostrar las diversas funcionalidades de ZUUL, en <a class="url" href="http://localhost:8080/api" target="_blank" rel="noopener noreferrer">http://localhost:8080/api</a> estará escuchando un servicio REST que esta implementada en la clase **TestController** de este proyecto. Esta clase simplemente devuelve en el cuerpo, los datos de la petición recibida.

```
@RestController
public class TestController {
	final  static String SALTOLINEA="\n";
	
	Logger log = LoggerFactory.getLogger(TestController.class); 
	@RequestMapping(path="/api")
	public String test(HttpServletRequest request)
	{
		StringBuffer strLog=new StringBuffer();
		
		strLog.append("................ RECIBIDA PETICION EN /api ......  "+SALTOLINEA);
		strLog.append("Metodo: "+request.getMethod()+SALTOLINEA);
		strLog.append("URL: "+request.getRequestURL()+SALTOLINEA);
		strLog.append("Host Remoto: "+request.getRemoteHost()+SALTOLINEA);
		strLog.append("----- MAP ----"+SALTOLINEA);
		request.getParameterMap().forEach( (key,value) -&gt;
		{
			for (int n=0;n&lt;value.length;n++)
			{
				strLog.append("Clave:"+key+ " Valor: "+value[n]+SALTOLINEA);
			}
		} );
		
		strLog.append(SALTOLINEA+"----- Headers ----"+SALTOLINEA);
		Enumeration&lt;String&gt; nameHeaders=request.getHeaderNames();				
		while (nameHeaders.hasMoreElements())
		{
			String name=nameHeaders.nextElement();
			Enumeration&lt;String&gt; valueHeaders=request.getHeaders(name);
			while (valueHeaders.hasMoreElements())
			{
				String value=valueHeaders.nextElement();
				strLog.append("Clave:"+name+ " Valor: "+value+SALTOLINEA);
			}
		}
		try {
			strLog.append(SALTOLINEA+"----- BODY ----"+SALTOLINEA);
			BufferedReader reader= request.getReader();
			if (reader!=null)
			{
				char[] linea= new char[100];
				int nCaracteres;
				while  ((nCaracteres=reader.read(linea,0,100))&gt;0)
				{				
					strLog.append( linea);
					
					if (nCaracteres!=100)
						break;
				} 
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
		log.info(strLog.toString());
		
		return SALTOLINEA+"---------- Prueba de ZUUL ------------"+SALTOLINEA+
				strLog.toString();
	}
}
```

### Filtrando: Dejando logs

En esta parte vamos a ver como crear un filtro de tal manera que se deje un registro de las peticiones realizadas.

Para ello crearemos la clase `PreFilter.java` la cual debe extender de **ZuulFilter**


    public class PreFilter extends ZuulFilter {
    	Logger log = LoggerFactory.getLogger(PreFilter.class); 
    	@Override
    	public Object run() {		
    		 RequestContext ctx = RequestContext.getCurrentContext();	    
    	     StringBuffer strLog=new StringBuffer();
    	     strLog.append("\n------ NUEVA PETICION ------\n");	    	    	    
    	     strLog.append(String.format("Server: %s Metodo: %s Path: %s \n",ctx.getRequest().getServerName()	    		 
    					,ctx.getRequest().getMethod(),
    					ctx.getRequest().getRequestURI()));
    	     Enumeration<String> enume= ctx.getRequest().getHeaderNames();
    	     String header;
    	     while (enume.hasMoreElements())
    	     {
    	    	 header=enume.nextElement();
    	    	 strLog.append(String.format("Headers: %s = %s \n",header,ctx.getRequest().getHeader(header)));	    			
    	     };	  	    
    	     log.info(strLog.toString());
    	     return null;
    	}
    
    	@Override
    	public boolean shouldFilter() {		
    		return true;
    	}
    
    	@Override
    	public int filterOrder() {
    		return FilterConstants.SEND_RESPONSE_FILTER_ORDER;
    	}
    
    	@Override
    	public String filterType() {
    		return "pre";
    	}
    
    }


En esta clase deberemos sobrescribir las funciones que vemos en el fuente. A continuación explico que haremos en cada de ellas

  * **public Object run()**Aquí pondremos lo que queremos que se ejecute por cada petición recibida. En ella podremos ver el contenido de la petición y manipularla si fuera necesario.
  * **public boolean shouldFilter()**Si devuelve **true** se ejecutara la función **run** .
  * **public int filterOrder()**Devuelve cuando que se ejecutara este filtro, pues normalmente hay diferentes filtros, para cada tarea. Hay que tener en cuenta que ciertas redirecciones o cambios en la petición hay que hacerlas en ciertos ordenes, por la misma lógica que tiene **zuul** a la hora de procesar las peticiones.
  * **public String filterType()** Especifica cuando se ejecutara el filtro. Si devuelve &#8220;pre&#8221; se ejecutara antes de que se haya realizado la redirección y por lo tanto antes de que se haya llamado al servidor final (a google en nuestro ejemplo).Si devuelve &#8220;post&#8221; se ejecutara después de que el servidor haya respondido a la petición.En la clase `org.springframework.cloud.netflix.zuul.filters.support.FilterConstants` tenemos definidos los tipos a devolver, PRE\_TYPE , POST\_TYPE,ERROR\_TYPE o ROUTE\_TYPE.

En la clase de ejemplo vemos como antes de realizar la petición al servidor final, se registran algunos datos de la petición, dejando un log con ellos.

Por último, para que Spring Boot utilize este filtro debemos añadir la función siguiente en nuestra clase principal.

    @Bean
    public PreFilter preFilter() {
            return new PreFilter();
     }


**Zuul** buscara _beans_ hereden de la clase **ZuulFilter** y los usara.

En este ejemplo, también esta la clase **PostFilter.java** que implementa otro filtro pero que se ejecuta después de realizar la petición al servidor final. Como he comentado esto se consigue devolviendo &#8220;post&#8221; en la función **filterType()**.

Para que **Zuul** use esta clase deberemos crear otro _bean_ con una función como esta:

     @Bean
     public PostFilter postFilter() {
            return new PostFilter();
     }


Recordar que también hay un filtro para tratar los errores y otro para tratar justo después de la redirección (&#8220;route&#8221;), pero en este articulo solo hablare de los filtros tipo &#8220;**post**&#8221; y tipo &#8220;**pre**&#8221;

Aclarar que aunque no lo trato en este articulo con **Zuul** no solo podemos redirigir hacia URL estáticas sino también a servicios, suministrados por Eureka Server, del cual hable en un articulo articulo. Además se integra con Hystrix para tener tolerancia a fallos, de tal manera que si no puede alcanzar un servidor se puede especificar que acción tomar.

  * ## Filtrando. Implementando seguridad

Añadamos una nueva redirección al fichero **application.yml**

     sensitiveHeaders: usuario, clave
      privado:
          path: /privado/**
          url: http://www.profesor-p.com  


Esta redirección llevara cualquier petición tipo <a class="url" href="http://localhost:8080/privado/LO_QUE_SEA" target="_blank" rel="noopener noreferrer">http://localhost:8080/privado/LO_QUE_SEA</a> a la pagina donde esta este articulo (<a class="url" href="http://www.profesor-p.com" target="_blank" rel="noopener noreferrer">http://www.profesor-p.com</a> )

La linea `sensitiveHeaders` la explicare más adelante.

En la clase `PreRewriteFilter`he implementando otro filtro tipo **pre** que trata solo esta redirección. ¿ como ?. Fácil, poniendo este código en la función `shouldFilter()`

    @Override
    public boolean shouldFilter() {				
    	return RequestContext.getCurrentContext().getRequest().getRequestURI().startsWith("/privado");
    }


Ahora en la función **run** incluimos el siguiente código

```
    Logger log = LoggerFactory.getLogger(PreRewriteFilter.class); 
	@Override
	public Object run() {		
		 RequestContext ctx = RequestContext.getCurrentContext();	    
	     StringBuffer strLog=new StringBuffer();
	     strLog.append("\n------ FILTRANDO ACCESO A PRIVADO - PREREWRITE FILTER  ------\n");	    
	     
	     try {	    	
    		 String url=UriComponentsBuilder.fromHttpUrl("http://localhost:8080/").path("/api").build().toUriString();
    		 String usuario=ctx.getRequest().getHeader("usuario")==null?"":ctx.getRequest().getHeader("usuario");
    		 String password=ctx.getRequest().getHeader("clave")==null?"":ctx.getRequest().getHeader("clave");
    		 
    	     if (! usuario.equals(""))
    	     {
    	    	if (!usuario.equals("profesorp") || !password.equals("profe"))
    	    	{
	    	    	String msgError="Usuario y/o contraseña invalidos";
	    	    	strLog.append("\n"+msgError+"\n");	  
	    	    	ctx.setResponseBody(msgError);
	    	    	ctx.setResponseStatusCode(HttpStatus.FORBIDDEN.value());
	    	    	ctx.setSendZuulResponse(false); 
	    	    	log.info(strLog.toString());	    	    	
	    	    	return null;
    	    	}
    	    	ctx.setRouteHost(new URL(url));
    	     }	    	     	    	
		} catch ( IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    
	     log.info(strLog.toString());
	     return null;
	}
```

Esta función busca en las cabeceras de la petición (_headers_) si  existe la cabecera **usuario**, en caso de no encontrarla no hace nada con lo cual redireccionara a `http://www.profesor-p.com` como se indica en el filtro. En el caso de que exista la cabecera **usuario** con el valor `profesorp`y que la variable **clave** tenga el valor `profe`, se redirigirá a `http://localhost:8080/api`. En caso contrario devolverá un código HTTP **FORBIDEN** devolviendo la cadena `"Usuario y/o contraseña invalidos"` en el cuerpo de la respuesta HTTP. Ademas se cancela el flujo de la petición debido a que se llama a **ctx.setSendZuulResponse(false)**

Debido a la linea **sensitiveHeaders** del fichero **application.yml** que he mencionado anteriormente las cabeceras &#8216;**usuario**&#8216; y &#8216;**clave**&#8216; no serán pasadas en el flujo de la petición.

Es muy importante que este filtro se ejecute despues del filtro de PRE_DECORATION, pues en caso contrario la llamada a <code class="language-java" lang="java">ctx.setRouteHost()</code> no tendra efecto. Por ello en la función filterOrder tenemos este código:

```
@Override
public int filterOrder() {
	return FilterConstants.PRE_DECORATION_FILTER_ORDER+1; 
}
```

Así una llamada pasando el usuario y la constraseña correctas, nos redirigira a http://localhost:8080/api

```
> curl -s -H "usuario: profesorp" -H "clave: profe" localhost:8080/privado

---------- Prueba de ZUUL ------------
................ RECIBIDA PETICION EN /api ......
Metodo: GET
URL: http://localhost:8080/api
Host Remoto: 127.0.0.1
----- MAP ----

----- Headers ----
Clave:user-agent Valor: curl/7.63.0
Clave:accept Valor: */*
Clave:x-forwarded-host Valor: localhost:8080
Clave:x-forwarded-proto Valor: http
Clave:x-forwarded-prefix Valor: /privado
Clave:x-forwarded-port Valor: 8080
Clave:x-forwarded-for Valor: 0:0:0:0:0:0:0:1
Clave:accept-encoding Valor: gzip
Clave:host Valor: localhost:8080
Clave:connection Valor: Keep-Alive

----- BODY ----
```

Si se pone mal la contraseña la salida seria esta:

```
> curl -s -H "usuario: profesorp" -H "clave: ERROR" localhost:8080/privado
Usuario y/o contraseña invalidos
```

###Filtrando. Filtrado dinámico

Para terminar incluiremos dos nuevas redirecciones en el fichero `applicaction.yml`

     local:
        path: /local/**
        url: http://localhost:8080/api
     url:
        path: /url/**
        url: http://url.com


En la primera cuando vayamos a la URL `http://localhost:8080/local/LO_QUE_SEA` seremos redirigidos a `http://localhost:8080/api/LO_QUE_SEA`. Aclarar que la etiqueta `local:`es arbitraria y podría poner `pepe` no teniendo porque coincidir con el _path_ que deseamos redirigir.

En la segunda cuando vayamos a la URL `http://localhost:8080/url/LO_QUE_SEA` seremos redirigidos a `http://localhost:8080/api/LO_QUE_SEA`

La clase **RouteURLFilter** sera la encargada de realizar tratar el filtro URL. Recordar que para que **Zuul** utilize los filtros debemos crear su correspondiente _bean._
```
@Bean
 public RouteURLFilter routerFilter() {
        return new RouteURLFilter();
 }
```

En la función **shouldFilter** de **RouteURLFilter** tendremos este código para que trate solo las peticiones a **/url.** 

    @Override
    	public boolean shouldFilter() {
    		RequestContext ctx = RequestContext.getCurrentContext();
    		if ( ctx.getRequest().getRequestURI() == null || ! ctx.getRequest().getRequestURI().startsWith("/url"))
    			return false;
    		return ctx.getRouteHost() != null
    				&& ctx.sendZuulResponse();
    	}


Este filtro será declarado del tipo **pre** en la función **filterType** por lo cual se ejecutara después de los filtros _pre_ y antes de ejecutar la redirección y llamar al servidor final.

    	@Override
    	public String filterType() {
    		return FilterConstants.PRE_TYPE;
    	}


En la función **run** esta el código que realiza la magia. Una vez hayamos capturado la _URL_ de destino y el _path_, como explico más adelante, es utilizada la función **setRouteHost()** del **RequestContext** para redirigirla adecuadamente.

    	@Override
    	public Object run() {
    		try {
    			RequestContext ctx = RequestContext.getCurrentContext();
    			URIRequest uriRequest;
    			try {
    				uriRequest = getURIRedirection(ctx);
    			} catch (ParseException k) {
    				ctx.setResponseBody(k.getMessage());
    				ctx.setResponseStatusCode(HttpStatus.BAD_REQUEST.value());
    				ctx.setSendZuulResponse(false);
    				return null;
    			}
    
    			UriComponentsBuilder uriComponent = UriComponentsBuilder.fromHttpUrl(uriRequest.getUrl());
    			if (uriRequest.getPath() == null)
    				uriRequest.setPath("/");
    			uriComponent.path(uriRequest.getPath());
    
    			String uri = uriComponent.build().toUriString();
    			ctx.setRouteHost(new URL(uri));
    		} catch (IOException k) {
    			k.printStackTrace();
    		}
    		return null;
    	}


Si encuentra en el _header_ la variable `hostDestino` será donde mandara la petición recibida. También buscara en la cabecera de la petición la variables `pathDestino` para  añadirla al `hostDestino`.

Por ejemplo, supongamos una petición como esta:

    > curl --header "hostDestino: http://localhost:8080" --header "pathDestino: api" \    localhost:8080/url?nombre=profesorp


La llamada será redirigida a <a class="url" href="http://localhost:8080/api?q=profesor-p" target="_blank" rel="noopener noreferrer">http://localhost:8080/api?q=profesor-p</a> y mostrara la siguiente salida:

    ---------- Prueba de ZUUL ------------
    ................ RECIBIDA PETICION EN /api ......
    Metodo: GET
    URL: http://localhost:8080/api
    Host Remoto: 127.0.0.1
    ----- MAP ----
    Clave:nombre Valor: profesorp
    
    ----- Headers ----
    Clave:user-agent Valor: curl/7.60.0
    Clave:accept Valor: */*
    Clave:hostdestino Valor: http://localhost:8080
    Clave:pathdestino Valor: api
    Clave:x-forwarded-host Valor: localhost:8080
    Clave:x-forwarded-proto Valor: http
    Clave:x-forwarded-prefix Valor: /url
    Clave:x-forwarded-port Valor: 8080
    Clave:x-forwarded-for Valor: 0:0:0:0:0:0:0:1
    Clave:accept-encoding Valor: gzip
    Clave:host Valor: localhost:8080
    Clave:connection Valor: Keep-Alive
    
    ----- BODY ----


​    

También puede recibir la URL a redireccionar en el cuerpo de la petición. El objeto JSON recibido debe tener el formato definido por la clase **GatewayRequest** que a su vez contiene un objeto **URIRequest**

    public class GatewayRequest {
    	URIRequest uri;
    	String body;
    
    }


    public class URIRequest {
    	String url;
    	String path;
    	byte[] body=null;


Este es un ejemplo de una redirección poniendo la URL destino en el body:

    curl -X POST \
      'http://localhost:8080/url?nombre=profesorp' \
      -H 'Content-Type: application/json' \
      -d '{
        "body": "El body chuli", "uri": { 	"url":"http://localhost:8080", 	"path": "api"    }
    }'


URL: &#8220;<a class="url" href="http://localhost:8080/url?nombre=profesorp" target="_blank" rel="noopener noreferrer">http://localhost:8080/url?nombre=profesorp</a>&#8221;

Cuerpo de la petición:

<pre><code class="language-json" lang="json">{
 "body": "El body chuli",
    "uri": {
    	"url":"http://localhost:8080",
    	"path": "api"
    }
}
</code></pre>

La salida recibida será:

    ---------- Prueba de ZUUL ------------
    ................ RECIBIDA PETICION EN /api ......
    Metodo: POST
    URL: http://localhost:8080/api
    Host Remoto: 127.0.0.1
    ----- MAP ----
    Clave:nombre Valor: profesorp
    
    ----- Headers ----
    Clave:user-agent Valor: curl/7.60.0
    Clave:accept Valor: */*
    Clave:content-type Valor: application/json
    Clave:x-forwarded-host Valor: localhost:8080
    Clave:x-forwarded-proto Valor: http
    Clave:x-forwarded-prefix Valor: /url
    Clave:x-forwarded-port Valor: 8080
    Clave:x-forwarded-for Valor: 0:0:0:0:0:0:0:1
    Clave:accept-encoding Valor: gzip
    Clave:content-length Valor: 91
    Clave:host Valor: localhost:8080
    Clave:connection Valor: Keep-Alive
    
    ----- BODY ----
    El body chuli


​    

Como se ve el cuerpo es tratado y al servidor final solo es mandado lo que se envía en el parámetro `body` de la petición **JSON**

Como se ve, **Zuul** tiene mucha potencia y es una excelente herramienta para realizar redirecciones. En este articulo solo he arañado las principales características de esta fantástica herramienta, pero espero que haya servido para ver las posibilidades que ofrece.

¡¡Nos vemos en la próxima entrada!!

[1]: https://cloud.spring.io/spring-cloud-netflix/multi/multi__router_and_filter_zuul.html
[2]: https://spring.io/projects/spring-cloud-netflix
[3]: https://github.com/chuchip/zuulSpringTest
[4]: https://marketplace.eclipse.org/content/spring-tools-4-spring-boot-aka-spring-tool-suite-4
[5]: https://raw.githubusercontent.com/chuchip/zuulSpringTest/master/starters.png
[6]: https://raw.githubusercontent.com/chuchip/zuulSpringTest/master/springio.png
[7]: https://raw.githubusercontent.com/chuchip/zuulSpringTest/master/estructura_ficheros.png