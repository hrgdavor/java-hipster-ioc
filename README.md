# java-hipster-ioc (well, maybe not IOC, but something similar)

Makes context almost as simple as regular bean.

Some goals (ATM it guides development, and list will change as code settles a bit)
- No discovery at runtime
- items that are discovered written into actual Java code to be compiled
- generates less code and aims to be readable
- Main concern is helping connect dependencies inside context, not go too fancy beyond that
- make it easier to discover/navigate how code is connected
- faster CI
- No effort will be made to be compatible with older Java. Min required ATM: 25
- No `@Scope` for now, all methods without parameters return singletons, factory methods return new instance each time
- circular dependencies are not allowed between beans
- circular dependencies not allowed between contexts
- Bean is not allowed to depend on the context in which it is defined
- BeanFactory is separate interface that context implementation implements, but must not the context itself
  - this allows for simpler code inside implementation ,as it has access to dependencies for the factory methods
- strict mode, disallow mixing module implementation methods and public abstract methods that expose beans,
  - to avoid temptation to call them in module code
  - calling them while context is not yet created (build helper method called from constructor) will cause freaky errors
  - maybe enforce this by analyzing AST (more complicated to implement)
  - or more naive version: search source file for method call `getBean1(`

  - non goals as it stands, not written in stone

- Lazy loading, 
  - closely following [stable values](stable.values.md) as alternative
  - beans in context are created immediately (also means there is no need for eagerLoad)
  - allow dependency of type: Supplier<Bean>
  - implement stable value polyfill and generate that code until JEP is finalised. 
    - use parameter to decide to generate stable value or polyfill based code.
    - this way no big refactor is needed, just flip the option or switch to version where stable value is default

generated code style
- generate region and endregion for sections
  - instance fields ( count > 5)
  - methods exposing public Beans  (count > 3)
  - BeanFactory methods (count > 3)
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
- for server with web,websocket, servlets:
  - jetty:12 has 33 jars, and 4.5MB
  - undertow:2.3 has 13 jars, and 4.5MB

## circular dependencies

This topic is bti circular in nature, I keep running in circles, to use or not to use circular dependencies.

I can say there at least should be effort to reduce.

Possible use-case for circular dependency 
 - splitting a large class into few classes (could be temporary until code is split to not need circular dep)

Formalize circular dependencies to be declared as such, so they can be configured to throw warning if intention is to have it as temporary fix, ot throw error when project is strict about it.

It must be via setter, constructor are not allowed to cause circular dependencies as it will not be feasible to combine them.

```java
@Circular 
public void setOtherDep(OtherDep other){
    this.other = other;
} 
```

# todo

generate dependency information as json

- list contexts
  - dependencies
  - exposed beans
  - expanded beans
  - factories
  - initializers
  - AutoCloseable beans
- dependencies that are exposed and those used in factories must be instance fields
- produce a markdown that is clickable and explains each module where you can click each class if you need more details
- can be used to produce a dependency graph
- maybe some nice HTML interface to explore dependencies
- make sure generated context does not call methods in methods that return a bean, to guarantee singleton as expected
  - if return value from an expanded dependency or a custom build method changes (like maybe config) we store a snapshot
  - it may be limiting, but is potential source of freaky bugs. dealing with mutable values should be done outside hipster-ioc
- make sure any dependency used for factories are placed in fields, and not just a local var in constructor
- explore if it would be a good practice to extract factory methods from context into a separate interface (this could be enforced)
- explore enforcing some rules that are deemed a good practice (with ability to  disable them via config or annotation if annoying to user)

dependency graph generation exploration ideas
- group by context, 
- mark dependencies for factory method separately from additional parameters.
- 

## Context definition

Define a public interface that is public facing part of your context. Other transitive dependencies that their dependencies resolved will
be part of the context, but not exposed.
```java
@HipsterContext(factory=CtxMainBeanFactory.class)
public abstract class CtxMain{
    
  public abstract ObjectMapper mapper();// getters are so yesterday
  public abstract SomeBean someBean(); // just do it like records :D

  // build method, when simply calling a constructor is not good enough
  protected ObjectMapper buildMapper(){
    ObjectMapper out = new ObjectMapper();
    out.addModule();
    return out;
  }
  
  // init method for beans that can be auto-created
  protected void initObjectMapper(ObjectMappe mapper){
    // name must follow convention "init"+classSimpleName
    // first parameter must be the bean, 
    // extra parameters if present, must be dependencies that can be resolved
    mapper.init();
  }
  
}
```

Define a public interface for bean factory to be able to inject it without creating dependency on the context itself.

```java
public interface CtxMainBeanFactory {
    // factory method for beans auto-created
    // name must follow convention "create"+classSimpleName
    // first parameter must be the bean, 
    // extra parameters if present, must be dependencies that can be resolved
    ReportWorker createReportTask(ReportConfig config);
}
```

The generated context implementation would be like this:

```java
public class CtxMainImpl extends CtxMain implements CtxMainBeanFactory{
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

You can declare a bean as Context even if there are no unimplemented methods (in that case, `Impl` class will not be created, but it will behave like
any auto-created context)

Factories with assisted Injection
 - convention : ContextName+(BeanFactory|Beans)
 - define in annotation
 - Similar to Assisted Injection - https://avaje.io/inject/#assistInject

# Expanding dependencies

When we make a context that depends on another context, all public beans from it will be made available to the new context.
(Manually created contexts will be expanded too)
Similar can be useful if we have a complex configuration class that has few/many sections defined as properties
Expanding dependencies does not generate more code by itself, just enables auto-generate to use the expanded dependencies when needed.

You can manually expand parts of an object as dependency easily by declaring a method in module. This exposes new bean for injection as dependency.
```java
public abstract class CtxMain{ 
    //...
    protected DbConfig dbConfig(MainConfig mc){ return mc.db();}
    protected EamailConfig emailConfig(MainConfig mc){ return mc.email();}
    //...
}
```

This adds a bit of code in module definition and generated code is tiny bit less easy to follow.

```java
  someBean = new SomeBean(dep1,dep2, emailConfig(mainConfig));
```

if we have another context visible inside the module as dependency, or a bean marked by `@HipsterContext(expandOnly=true)`, it is then allowed to expand public methods as bean suppliers. It then gives more straight forward generated code, and no manual boilerplate for this use-case.

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

Beans are allowed to inject own where they are defined context as dependency.
Beans must not call any methods of injected dependencies(other beans or contexts) except if it is an immutable record like config that is guaranteed to be loaded beforehand .

Main reason to inject own context as a dependency is for classes that need to call factory methods from the context.


# !!!!!!! ignore text below, AI mumbo jumbo, needs review, just a skeleton text


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
