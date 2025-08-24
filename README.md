# java-hipster-ioc (well, maybe not IOC, but something similar)

Some goals (ATM it guides development, and list will change as code settles a bit)
- No discovery at runtime
- items that are discovered written into actual Java code to be compiled
- generates less code and aims to be readable
- Main concern is helping connect dependencies inside context, not go too fancy beyond that
- make it easier to discover/navigate how code is connected
- faster CI
- No effort will be made to be compatible with older Java. Min required ATM: 25
- No `@Scope` for now, all methods without parameters return singletons, factory methods return new instance each time
- Lazy loading, not supported out of the box, will be looking into Java [stable values](stable.values.md)
- circular dependencies are not allowed between beans
- Bean is allowed to depend on the context in which it is defined ( not allowed to use inside the constructor, just store the reference, and use later

generated code style
- beans without dependencies are created in field initializer

Some random stuff
- I like to call contexts `CtxFoor CtxBar ...` for faster search/jump in IDE.
- I prefer to put generated classes in git (faster builds, generate is called when needed during development)
- I prefer generating classes over runtime byte code generation
- I hate Lombok for the fact it changes code directly as it compiles, and obscures what happens
- I use jackson for JSON stuff
- I wanted to try generated serializers and deserializers for jackson versus runtime ones.
- [micronaut-serde-jackson](README.json.serialization.md) looks very promising for jackson serializers generation

## Context definition

Define a public interface that is public facing part of your context
```java
public interface CtxMain extends CtxMainInternal{ 
    ObjectMapper mapper();
    SomeBean someBean();
}
```

Then define an interface, that is package private, for your code that will be part of the module, like builders.

```java
public interface CtxMainInternal{
    default ObjectMapper buildMapper(){
      ObjectMapper out = new ObjectMapper();
      out.init();
      return out;
    }
}
```

```java
public class CtxMainImpl implements CtxMain{
  protected final ObjectMapper mapper;
  protected final SomeBean someBean;

  public CtxMainImpl(){
    mapper = new ObjectMapper(); 
    someBean = new SomeBean(mapper);
  }
  @Override public ObjectMapper mapper(){ return mapper; }
  @Override public SomeBean someBean(){ return someBean; }
}
```

The generated module would be


## Maven project structure suggestions(for faster build)

Start with multi module project even for small things, to get used to it (it is not a big hassle). And if it grows you will be ready to modularize (it can speed up build time too). 

Split beans into multiple contexts, to eventually split the code into modules. This way it is ready for refactor, after code settles (some requirements become visible after initial implementation). If the project has obvious candidates for modularization at the start, ofc. split them into modules right away.

**Performance benefits (build time)**

- both gradle and maven cache whole modules during build, so having multiple modules gives more chance for cache hit
- modules that depend only on some core module(s) can be built in parallel for further speed gain 

**Some additional thoughts**



Having multiple modules does not mean you should immediately run them as microservices. I do not usually use microservices, but when I do I use them only sparsely for jobs that are compute heavy. Scaling code that just does database queries is silly, as the most heavy lifting is done in the database, and there are many ways to optimize before scaling is a must (for example: read from slaves and write to master.

## Live reload for configuration

Many parts of an application if not all can be implemented to allow for config change without restart, and depending on the type of process can be even trivial to do.

It is critical to implement validation, to reject a config change that would break something. Most trivial validation will come from config parser depending on config format.

- tasks that start and finish relatively (minutes hours) can be setup to read latest snapshot of the config, thus every task that starts after config change can have the latest configuration



# Contexts, structured and many

Context also can have dependencies like beans. ... maybe they are not too diferent

Services are allowed to inject whole world as dependency, just not get services from it in constructor.

Main reason to inject own world as a dependency is for classes that create parent context



- **Independently Loaded Contexts:**
  You can create completely separate `ApplicationContext` instances for different modules or functional parts. Each manages its own beans independently, useful for large, modular systems or for isolating parts of your app during testing.
- **Parent-Child (Hierarchical) Contexts:**
  You can set up a hierarchy of contexts where:
  - A **parent** context defines shared beans.
  - **Child** contexts inherit beans from the parent but can define their own or override parent beans.
  - Beans in the child context can access those defined in the parent; the parent cannot see child beans.
  - This is often used in complex web applications where the root context holds shared services/config and child contexts hold web-specific beans.


## Typical Use Cases for multiple contexts

- **Modular Applications:** Each module manages its own context to isolate configuration and dependencies.
- **Shared Core Configuration:** Common services/config in a parent context, with specialized beans in children.
- **Testing:** Create isolated contexts per test for finer-grained unit/integration tests.
- **Multi-tenant or Plugin Architectures:** Separate contexts for tenants/plugins for isolation.

------

## Limitations & Caveats

- **Bean Visibility:** Child can access parent beans but not vice versa.
- **Lifecycle Management:** Each context manages its own lifecycle; ensure proper startup/shutdown sequence.
------

## Practical Example

- Parent context (core beans: security, data sources)
- Child context (web beans: controllers, view resolvers)
- Web app: Root context → parent; DispatcherServlet context → child
