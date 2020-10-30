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



 En cuanto a **dummyRest** es una aplicación que simplemente responde en unas rutas con un mensaje que incluye las características de la petición:

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

El servidor **eureakaclient1**, es un servidor tipo reactivo, que se registra como cliente en un servidor Eureka. El código es el siguiente:

````java
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

Notar que para mostrar los datos de la petición debemos tratar el objeto **ServerHttpRequest** y no el objeto **HttpServletRequest** como en dummyrest, Esto es porque **dummyrest** es un servidor tradicional, basado en J2EE , con Tomcat como servidor de aplicaciones embebido y **eureakaclient1** utiliza Jetty como servidor de aplicaciones,  utilizando la tecnología webflux.

### Creando la aplicación de gateway

Ahora vamos a a empezar a hablar directamente del **Gateway**, que es de lo que va este articulo.

Si tenemos instalado _Eclipse_ con el [plugin de _Spring Boot_][4] (lo cual recomiendo), el crear el proyecto seria tan fácil como añadir un nuevo proyecto del tipo _Spring Boot_ incluyendo el _starter_ **Spring Cloud Gateway**. 



![dependencias](.\dependencias.png)

Para poder hacer algunas pruebas también incluiremos el _starter_ **Hystrix** y el **cliente de Eureka.**

También tenemos la opción de crear un proyecto Maven desde la página web <a class="url" href="https://start.spring.io/" target="_blank" rel="noopener noreferrer">https://start.spring.io/</a> que luego importaremos desde nuestro IDE preferido.

### Empezando

Spring Cloud Gateway tiene [una excelente documentación](https://cloud.spring.io/spring-cloud-gateway/reference/html/)  donde explica todas las diferentes opciones que tenemos para realizar las redirecciones. Básicamente una ruta tiene siempre 3 partes.

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

```bash
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

> curl -s -D - -H "host:www.dummy.com" http://localhost:8080/PARAM1
...
En dummy con parametro PARAM1 de DummyRest
...
```

### Definiendo rutas en el programa

Lo de poder definir las rutas en un fichero de propiedades esta fenomenal pero quizás tu prefieras hacerlo en tu programa, pues no hay problema. Tan solo tienes que crear una función que devuelva un *bean* tipo **RouteLocator**. En este ejemplo añadimos esta función:

```java
@Bean
public RouteLocator myRoutes(RouteLocatorBuilder builder) {
    return builder.routes()
        .route(p -> p
               .path("/custom/**")
               .uri("http://localhost:8000"))
        .route(p -> p
               .path("/fallo")
               .filters(f -> f
                        .hystrix(config -> config
                                 .setName("mycmd")
                                 .setFallbackUri("forward:/fallback")))
               .uri("http://localhost:999"))
        .build();
}
```

A través del builder, vamos añadiendo rutas, prácticamente igual que lo haríamos anteriormente en el fichero *yaml* . 

En este caso hemos añadido una una ruta para cuando el path empiece por **/custom** que nos llevara a http://localhost:80000 y otra ruta que se llevara a cabo cuando el path sea **/fallo**. En este caso, le hemos añadido un filtro tipo *[hystrix](https://spring.io/guides/gs/circuit-breaker/)*, el cual hará que si no puede llegar a la *uri* especificada (http://localhost:999") vaya  a **/fallback. Para que esto funcione hemos de añadir la siguiente función en nuestro programa. Como podéis adivinar ira a la función fallback() en caso de error.

```
@RequestMapping("/fallback")
public Mono<String> fallback() {
return Mono.just("Algo fue mal. Respondido de fallback");
}
```



###  Filtrando: Dejando logs

Hay veces en que queremos añadir logs para las llamadas que recibamos. Una de  las opciones para hacer esto es crear  un **bean** tipo  **GlobalFilter**. 

Para ello creaeremos una clase que implemente ese interfaz, y en la función **filter**  pondremos el código para escribir nuestros logs.

````java
@Bean
class CustomGlobalFilter implements GlobalFilter, Ordered {
	Logger log = LoggerFactory.getLogger(this.getClass());
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("custom global filter. "+exchange.getRequest().getPath().toString());
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
````

En este caso simplemente pondremos un log indicando el path de la llamada realizada y después permitimos seguir el proceso, devolviendo el control  al flujo. También es posible romper el flujo y por supuesto manipularlo, añadiendo y/o borrando cabeceras.

Con la función getOrder() indicamos cuando se debe ejecutar este filtro. Cuanto más bajo sea el numero devuelto antes se ejecutara en el flujo del enrutamiento.

**Spring Cloud Gateway** tiene mucha más potencia que la que he enseñado aquí. Podemos definir nuestros filtros específicos para cada llamada además de tener uno genérico,  podemos implementar seguridad y muchas cosas. Os invito a que echéis un vistazo a este articulo de [Baeldung](https://www.baeldung.com/spring-cloud-custom-gateway-filters) donde profundiza más en estos asuntos. 

¡¡ Espero que esta entrada haya sido útil y nos vemos en la proxima!!

[4]: https://marketplace.eclipse.org/content/spring-tools-4-spring-boot-aka-spring-tool-suite-4
[5]: https://raw.githubusercontent.com/chuchip/zuulSpringTest/master/starters.png