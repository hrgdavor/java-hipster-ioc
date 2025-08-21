# java-hipster-ioc

Pragmatic IOC, lightweight and fast to build.

- No discovery at runtime
- items that are discovered written into actual Java code to be compiled
- generates less code and aims to be readable
- Main concern is helping connect dependencies inside context, not go too fancy beyond that
- make it easier to discover/navigate how code is connected
- faster CI
- No effort will be made to be compatible with older Java. Min required ATM: 25
- No `@Scope` for now, all methods return singletons
- Lazy loading, not supported out of the box, will be looking into Java [stable values](stable.values.md)

## Context definition



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

Context can be organized into a tree.

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



## Creating Multiple Contexts

**Basic Example (Java Config):**

```
java// Parent context
ApplicationContext parentContext = new AnnotationConfigApplicationContext(ParentConfig.class);

// Child context
AnnotationConfigApplicationContext childContext = new AnnotationConfigApplicationContext();
childContext.setParent(parentContext);
childContext.register(ChildConfig.class);
childContext.refresh();
```

- Beans from `ParentConfig` are accessible in `childContext`.

------

## Spring Boot: Using SpringApplicationBuilder

Spring Boot allows context hierarchies using `SpringApplicationBuilder`:

```java
new SpringApplicationBuilder()
    .sources(MainApplication.class)
    .child(DataContextConfiguration.class)
    .sibling(WebContextConfiguration.class)
    .run(args);
```

- You can have a parent with multiple children (siblings) where each child can't access siblings' beans but can access the parent's beans.

------

## Typical Use Cases

- **Modular Applications:** Each module manages its own context to isolate configuration and dependencies.
- **Shared Core Configuration:** Common services/config in a parent context, with specialized beans in children.
- **Testing:** Create isolated contexts per test for finer-grained unit/integration tests.
- **Multi-tenant or Plugin Architectures:** Separate contexts for tenants/plugins for isolation.

------

## Limitations & Caveats

- **Bean Visibility:** Child can access parent beans but not vice versa.
- **Lifecycle Management:** Each context manages its own lifecycle; ensure proper startup/shutdown sequence.
- **Spring Boot Defaults:** By default, only one context is created. Hierarchies are advanced cases—use `SpringApplicationBuilder` for full control.

------

## Practical Example

- Parent context (core beans: security, data sources)
- Child context (web beans: controllers, view resolvers)
- Web app: Root context → parent; DispatcherServlet context → child
