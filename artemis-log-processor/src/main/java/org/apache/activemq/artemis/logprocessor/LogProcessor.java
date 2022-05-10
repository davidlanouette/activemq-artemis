/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.logprocessor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import org.apache.activemq.artemis.logprocessor.annotation.LogBundle;
import org.apache.activemq.artemis.logprocessor.annotation.GetLogger;
import org.apache.activemq.artemis.logprocessor.annotation.LogMessage;
import org.apache.activemq.artemis.logprocessor.annotation.Message;

@SupportedAnnotationTypes({"org.apache.activemq.artemis.logprocessor.annotation.LogBundle"})
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class LogProcessor extends AbstractProcessor {

   @Override
   public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      HashMap<Integer, String> messages = new HashMap<>();

      try {

         System.out.println("*******************************************************************************************************************************");
         for (TypeElement e : annotations) {
            System.out.println("e::" + e);
            for (Element annotatedTypeEl : roundEnv.getElementsAnnotatedWith(e)) {
               System.out.println("annotated::" + annotatedTypeEl);
            }
         }
         System.out.println("*******************************************************************************************************************************");

         for (TypeElement annotation : annotations) {
            System.out.println("Annotation: " + annotation);
            for (Element annotatedTypeEl : roundEnv.getElementsAnnotatedWith(annotation)) {

               TypeElement annotatedType = (TypeElement) annotatedTypeEl;

               LogBundle bundleAnnotation = annotatedType.getAnnotation(LogBundle.class);

               String fullClassName = annotatedType.getQualifiedName() + "_impl";
               String interfaceName = annotatedType.getSimpleName().toString();
               String simpleClassName = interfaceName + "_impl";
               JavaFileObject fileObject = processingEnv.getFiler().createSourceFile(fullClassName);
               System.out.println("file::" + fileObject);
               PrintWriter writerOutput = new PrintWriter(fileObject.openWriter());

               // header
               writerOutput.println("/** This class is auto generated by " + LogProcessor.class.getCanonicalName());
               writerOutput.println("    and it inherits whatever license is declared at " + annotatedType + " */");
               writerOutput.println();

               // opening package
               writerOutput.println("package " + annotatedType.getEnclosingElement() + ";");
               writerOutput.println();

               writerOutput.println("import org.slf4j.Logger;");
               writerOutput.println("import org.slf4j.LoggerFactory;");
               writerOutput.println("import org.slf4j.helpers.FormattingTuple;");
               writerOutput.println("import org.slf4j.helpers.MessageFormatter;");

               writerOutput.println();

               // Opening class
               writerOutput.println("// " + bundleAnnotation.toString());
               writerOutput.println("public class " + simpleClassName + " implements " + interfaceName);
               writerOutput.println("{");

               writerOutput.println("   private final Logger logger;");
               writerOutput.println();

               writerOutput.println("   public " + simpleClassName + "(Logger logger ) {");
               writerOutput.println("      this.logger = logger;");
               writerOutput.println("   }");
               writerOutput.println();

               writerOutput.println("   public " + simpleClassName + "() {");
               writerOutput.println("      this(LoggerFactory.getLogger(" + fullClassName + ".class));");
               writerOutput.println("   }");
               writerOutput.println();


               // Declaring the static field that's used by {@link I18NFactory}
               writerOutput.println("   public static " + simpleClassName + " INSTANCE = new " + simpleClassName + "();");
               writerOutput.println();

               for (Element el : annotatedType.getEnclosedElements()) {
                  if (el.getKind() == ElementKind.METHOD) {

                     ExecutableElement executableMember = (ExecutableElement) el;

                     Message messageAnnotation = el.getAnnotation(Message.class);
                     LogMessage logAnnotation = el.getAnnotation(LogMessage.class);
                     GetLogger getLogger = el.getAnnotation(GetLogger.class);


                     if (messageAnnotation != null && logAnnotation != null && getLogger != null) { //This requires all 3 are set to fail, it wont pick up other combinations as the message suggests might be wanted.
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Cannot use combied annotations " + el);
                        return false;
                     }

                     if (messageAnnotation != null) {
                        generateMessage(bundleAnnotation, writerOutput, executableMember, messageAnnotation, messages);
                     } else if (logAnnotation != null) {
                        generateLogger(bundleAnnotation, writerOutput, executableMember, logAnnotation, messages);
                     } else if (getLogger != null) {
                        generateGetLogger(bundleAnnotation, writerOutput, executableMember, getLogger);
                     }
                  }
               }

               writerOutput.println("}");

               writerOutput.close();
            }
         }
      } catch (Exception e) {
         e.printStackTrace();
         processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
         return false;
      }

      return true;
   }

   private void generateMessage(LogBundle bundleAnnotation,
                                PrintWriter writerOutput,
                                ExecutableElement executableMember,
                                Message messageAnnotation,
                                HashMap<Integer, String> processedMessages) {

      String previousMessage = processedMessages.get(messageAnnotation.id()); // Could move inside the if?

      if (processedMessages.containsKey(messageAnnotation.id())) {
         throw new IllegalStateException("message " + messageAnnotation.id() + " with definition = " + messageAnnotation.value() + " was previously defined as " + previousMessage);
      }

      processedMessages.put(messageAnnotation.id(), messageAnnotation.value());

      // This is really a debug output
      writerOutput.println("   // " + encodeSpecialChars(messageAnnotation.toString()));

      writerOutput.write("   public " + executableMember.getReturnType() + " " + executableMember.getSimpleName() + "(");

      Iterator<? extends VariableElement> parameters = executableMember.getParameters().iterator();

      boolean hasParameters = false;

      // the one that will be used on the call
      StringBuffer callList = new StringBuffer();
      while (parameters.hasNext()) {
         hasParameters = true;
         VariableElement parameter = parameters.next();
         writerOutput.write(parameter.asType() + " " + parameter.getSimpleName());
         callList.append(parameter.getSimpleName());
         if (parameters.hasNext()) {
            writerOutput.write(", ");
            callList.append(",");
         }
      }

      // the real implementation
      writerOutput.println(")");
      writerOutput.println("   {");

      String formattingString = encodeSpecialChars(bundleAnnotation.projectCode() + messageAnnotation.id() + " " + messageAnnotation.value());
      if (!hasParameters) {
         writerOutput.println("      String returnString = \"" + formattingString + "\";");
      } else {
         writerOutput.println("      FormattingTuple tuple = MessageFormatter.format(\"" + formattingString + "\"," + callList + ");");
         writerOutput.println("      String returnString = tuple.getMessage();");
      }

      if (executableMember.getReturnType().toString().equals(String.class.getName())) {
         writerOutput.println("      return returnString;");
      } else {
         writerOutput.println("      return new " + executableMember.getReturnType().toString() + "(returnString);");
      }

      writerOutput.println("   }");
      writerOutput.println();
   }

   private String encodeSpecialChars(String input) {
      return input.replaceAll("\n", "\\\\n").replaceAll("\"", "\\\\\"");
   }


   private void generateGetLogger(LogBundle bundleAnnotation,
                                  PrintWriter writerOutput,
                                  ExecutableElement executableMember,
                                  GetLogger loggerAnnotation) {

      // This is really a debug output
      writerOutput.println("   // " + loggerAnnotation.toString());
      writerOutput.println("   public Logger " + executableMember.getSimpleName() + "() { return logger; }");
      writerOutput.println();
   }


   private void generateLogger(LogBundle bundleAnnotation,
                               PrintWriter writerOutput,
                               ExecutableElement executableMember,
                               LogMessage messageAnnotation,
                               HashMap<Integer, String> processedMessages) {

      String previousMessage = processedMessages.get(messageAnnotation.id());

      if (processedMessages.containsKey(messageAnnotation.id())) {
         throw new IllegalStateException("message " + messageAnnotation.id() + " with definition = " + messageAnnotation.value() + " was previously defined as " + previousMessage);
      }

      processedMessages.put(messageAnnotation.id(), messageAnnotation.value());

      // This is really a debug output
      writerOutput.println("   // " + encodeSpecialChars(messageAnnotation.toString()));

      writerOutput.write("   public void " + executableMember.getSimpleName() + "(");

      Iterator<? extends VariableElement> parameters = executableMember.getParameters().iterator();

      boolean hasParameters = false;

      // the one that will be used on the call
      StringBuffer callList = new StringBuffer();
      while (parameters.hasNext()) {
         hasParameters = true;
         VariableElement parameter = parameters.next();
         writerOutput.write(parameter.asType() + " " + parameter.getSimpleName());
         callList.append(parameter.getSimpleName());
         if (parameters.hasNext()) {
            writerOutput.write(", ");
            callList.append(",");
         }
      }

      // the real implementation
      writerOutput.println(")");
      writerOutput.println("   {");

      String methodName;

      switch (messageAnnotation.level()) {
         case WARN:
            methodName="warn"; break;
         case INFO:
            methodName="info"; break;
         case ERROR:
            methodName="error"; break;
         default:
            throw new IllegalStateException("illegal method level " + messageAnnotation.level());
      }

      String formattingString = encodeSpecialChars(bundleAnnotation.projectCode() + messageAnnotation.id() + " " + messageAnnotation.value());
      if (!hasParameters) {
         writerOutput.println("      logger." + methodName + "(\"" + formattingString + "\");");
      } else {
         writerOutput.println("      logger." + methodName + "(\"" + formattingString + "\", " + callList + ");");
      }
      writerOutput.println("   }");
      writerOutput.println();
   }
}
