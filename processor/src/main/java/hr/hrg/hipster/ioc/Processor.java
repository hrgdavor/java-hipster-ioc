package hr.hrg.hipster.ioc;

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;

@SupportedAnnotationTypes({"nexios.ava.ConfigSource"})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class Processor extends AbstractProcessor{
	
	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		System.err.println("annotations  "+annotations.size());
		 if (roundEnv.processingOver() || annotations.size() == 0) {
	            return false;
	        }
		MethodSpec main = MethodSpec.methodBuilder("main")
			    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
			    .returns(void.class)
			    .addParameter(String[].class, "args")
			    .addStatement("$T.out.println($S)", System.class, "Hello, JavaPoet 2!")
			    .build();

			TypeSpec helloWorld = TypeSpec.classBuilder("HelloWorld")
			    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
			    .addMethod(main)
			    .build();

			JavaFile javaFile = JavaFile.builder("com.example.helloworld", helloWorld)
			    .build();

			try {				
				javaFile.writeTo(System.out);		
				javaFile.writeTo(processingEnv.getFiler());		
			} catch (Exception e) {
				e.printStackTrace();
			}
		return false;
	}
}
