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
- beans without dependencies are created in field initializer of ContextImpl
- beans dependencies will decide sorting order for creating, and same sorting order must be for init* methods

- Some random stuff
- I like to call contexts `CtxFoo CtxBar ...` for faster search/jump in IDE.
- I prefer to put generated classes in git (faster builds, generate is called when needed during development)
- I prefer generating classes over runtime byte code generation
- I hate Lombok for the fact it changes code directly as it compiles, and obscures what happens
- I use jackson for JSON stuff
- I wanted to try generated serializers and deserializers for jackson versus runtime ones.
- [micronaut-serde-jackson](README.json.serialization.md) looks very promising for jackson serializers generation

# todo

generate dependency information as json

- list contexts
  - dependencies
  - exposed beans
  - expanded beans
  - factories
  - initializers
  - AutoCloseable beans
- produce a markdown that is clickable and explains each module where you can click each class if you need more details
- can be used to produce a dependency graph
- maybe some nice HTML interface to explore dependencies
- make sure generated context does not call methods in methods that return a bean, to guarantee singleton as expected
  - if return value from an expanded dependency or a custom build method changes (like maybe config) we store a snapshot
  - it may be limiting, but is potential source of freaky bugs. dealing with mutable values should be done outside of hipster-ioc

## Context definition

Define a public interface that is public facing part of your context. Other transitive dependencies that their dependencies resolved will
be part of the context, but not exposed.
```java
public interface CtxMain extends CtxMainInternal{

    // factory method for beans auto-created
    // name must follow convention "create"+classSimpleName
    // first parameter must be the bean, 
    // extra parameters if present, must be dependencies that can be resolved
    ReportWorker createReportTask(ReportConfig config);
    
    ObjectMapper mapper();// getters are so yesterday
    SomeBean someBean(); // just do it like records :D
}
```
You can declare a bean as Context even if there are no unimplemented methods (in that case, `Impl` class will not be created, but it will behave like 
any auto-created context)

Then define an interface, that is package private, for your code that will be part of the module, like builders.

```java
public interface CtxMainInternal{
    // build method, when simply calling a constructor is not good enough
    default ObjectMapper buildMapper(){
      ObjectMapper out = new ObjectMapper();
      out.addModule();
      return out;
    }
    // init method for beans that can be auto-created
    void initObjectMapper(ObjectMappe mapper){
      // name must follow convention "init"+classSimpleName
      // first parameter must be the bean, 
      // extra parameters if present, must be dependencies that can be resolved
        mapper.init();
    }
    
}
```

The generated context implementation would be like this:

```java
/* auto generated context implementation */
public class CtxMainImpl implements CtxMain{
  protected final ObjectMapper mapper;
  protected final SomeBean someBean;

  public CtxMainImpl(){
    mapper = buildMapper();
    someBean = new SomeBean(mapper);
    initObjectMapper(mapper);
  }
  /** ReportWorker factory */
  @Override ReportWorker createReportTask(ReportConfig config){
      return new ReportConfig(mapper, someBean, config);
  }
  @Override public ObjectMapper mapper(){ return mapper; }
  @Override public SomeBean someBean(){ return someBean; }
}
```

# Expanding dependencies

When we make a context that depends on another context, all public beans from it will be made available to the new context.
(Manually created contexts will be expanded too)
Similar can be useful if we have a complex configuration class that has few/many sections defined as properties
Expanding dependencies does not generate more code by itself, just enables autp-generate to use the expanded dependencies when needed.

You can manually expand parts of an object easily by declaring a method in internal interface.
This exposes new bean for injection into others as dependency
```java
public interface CtxMainInternal { 
    DbCinfig dbConfig(MainConfig mc){ return mc.db();}
    EamailConfig emailConfig(MainConfig mc){ return mc.email();}   
}
```

This adds a bit of code in internal module definition and code tiny bit less trackable when generated
```java
  someBean = new SomeBean(dep1,dep2, emailConfig(mainConfig));
```

if automated by an annotation or some other conventioin to allow some objects or parts of to be expanded code in module could be more clear

```java
  someBean = new SomeBean(dep1,dep2, mainConfig.email());
```

# try to follow this pattern manually

This is work in progress, and is meant to give high level goals of separating code in such way that writing it manually is not huge a hassle and at the same time can be autogenerated.

- maximize object instantiation inside module
- configure inside module
- initialize inside module
- create factory inside module, in case created objects need dependencies along with other options
- do the work inside the bean

# when context implementation is auto-generated

- composition easier than extending a base class, especially when the base class has many dependencies 
- adding dependencies to a base class is a pain that can be avoided this way

## Maven project structure suggestions(for faster build)

Start with multi-module project even for small things, to get used to it (it is not a big hassle). And if it grows you will be ready to modularize (it can speed up build time too). 

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
