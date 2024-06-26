/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.protocol.amqp.converter.message;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.protocol.amqp.converter.AMQPMessageSupport;
import org.apache.qpid.proton.Proton;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.message.Message;
import org.junit.jupiter.api.Test;

public class AMQPMessageSupportTest {

   // ---------- getSymbol ---------------------------------------------------//

   @Test
   public void testGetSymbol() {
      assertNotNull(AMQPMessageSupport.getSymbol("x-opt-something-or-other"));
   }

   // ---------- getMessageAnnotation ----------------------------------------//

   @Test
   public void testGetMessageAnnotationWhenMessageHasAnnotationsMap() {
      Map<Symbol, Object> messageAnnotationsMap = new HashMap<>();
      messageAnnotationsMap.put(Symbol.valueOf("x-opt-test"), Boolean.TRUE);
      Message message = Proton.message();
      message.setMessageAnnotations(new MessageAnnotations(messageAnnotationsMap));

      assertNotNull(AMQPMessageSupport.getMessageAnnotation("x-opt-test", message));
   }

   @Test
   public void testGetMessageAnnotationWhenMessageHasEmptyAnnotationsMap() {
      Map<Symbol, Object> messageAnnotationsMap = new HashMap<>();
      Message message = Proton.message();
      message.setMessageAnnotations(new MessageAnnotations(messageAnnotationsMap));

      assertNull(AMQPMessageSupport.getMessageAnnotation("x-opt-test", message));
   }

   @Test
   public void testGetMessageAnnotationWhenMessageHasNoAnnotationsMap() {
      Message message = Proton.message();
      assertNull(AMQPMessageSupport.getMessageAnnotation("x-opt-test", message));
   }

   @Test
   public void testGetMessageAnnotationWhenMessageIsNull() {
      assertNull(AMQPMessageSupport.getMessageAnnotation("x-opt-test", null));
   }

   // ---------- isContentType -----------------------------------------------//

   @Test
   public void testIsContentTypeWithNullStringValueAndNullMessageContentType() {
      Message message = Proton.message();
      assertTrue(AMQPMessageSupport.isContentType(null, message));
   }

   @Test
   public void testIsContentTypeWithNonNullStringValueAndNullMessageContentType() {
      Message message = Proton.message();
      assertFalse(AMQPMessageSupport.isContentType("test", message));
   }

   @Test
   public void testIsContentTypeWithNonNullStringValueAndNonNullMessageContentTypeNotEqual() {
      Message message = Proton.message();
      message.setContentType("fails");
      assertFalse(AMQPMessageSupport.isContentType("test", message));
   }

   @Test
   public void testIsContentTypeWithNonNullStringValueAndNonNullMessageContentTypeEqual() {
      Message message = Proton.message();
      message.setContentType("test");
      assertTrue(AMQPMessageSupport.isContentType("test", message));
   }

   @Test
   public void testIsContentTypeWithNullStringValueAndNonNullMessageContentType() {
      Message message = Proton.message();
      message.setContentType("test");
      assertFalse(AMQPMessageSupport.isContentType(null, message));
   }
}
