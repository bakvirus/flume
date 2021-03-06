/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.flume.channel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.flume.Channel;
import org.apache.flume.ChannelException;
import org.apache.flume.ChannelSelector;
import org.apache.flume.Event;
import org.apache.flume.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChannelProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(
      ChannelProcessor.class);

  private final ChannelSelector selector;

  public ChannelProcessor(ChannelSelector selector) {
    this.selector = selector;
  }

  public ChannelSelector getSelector() {
    return selector;
  }

  public void processEventBatch(List<Event> events) {
    Map<Channel, List<Event>> reqChannelQueue =
        new LinkedHashMap<Channel, List<Event>>();

    Map<Channel, List<Event>> optChannelQueue =
        new LinkedHashMap<Channel, List<Event>>();

    for (Event event : events) {
      List<Channel> reqChannels = selector.getRequiredChannels(event);

      for (Channel ch : reqChannels) {
        List<Event> eventQueue = reqChannelQueue.get(ch);
        if (eventQueue == null) {
          eventQueue = new ArrayList<Event>();
          reqChannelQueue.put(ch, eventQueue);
        }
        eventQueue.add(event);
      }

      List<Channel> optChannels = selector.getOptionalChannels(event);

      for (Channel ch: optChannels) {
        List<Event> eventQueue = optChannelQueue.get(ch);
        if (eventQueue == null) {
          eventQueue = new ArrayList<Event>();
          optChannelQueue.put(ch, eventQueue);
        }

        eventQueue.add(event);
      }
    }

    // Process required channels

    for (Channel reqChannel : reqChannelQueue.keySet()) {
      Transaction tx = null;
      try {
        tx = reqChannel.getTransaction();
        tx.begin();

        List<Event> batch = reqChannelQueue.get(reqChannel);

        for (Event event : batch) {
          reqChannel.put(event);
        }

        tx.commit();
      } catch (ChannelException ex) {
        tx.rollback();
        throw ex;
      } finally {
        if (tx != null) {
          tx.close();
        }
      }
    }

    // Process optional channels
    for (Channel optChannel : optChannelQueue.keySet()) {
      Transaction tx = null;
      try {
        tx = optChannel.getTransaction();
        tx.begin();

        List<Event> batch = optChannelQueue.get(optChannel);

        for (Event event : batch ) {
          optChannel.put(event);
        }

        tx.commit();
      } catch (ChannelException ex) {
        tx.rollback();
        LOG.warn("Unable to put event on optional channel", ex);
      } finally {
        if (tx != null) {
          tx.close();
        }
      }
    }
  }

  public void processEvent(Event event) {

    // Process required channels
    List<Channel> requiredChannels = selector.getRequiredChannels(event);
    for (Channel requriedChannel : requiredChannels) {
      Transaction tx = null;
      try {
        tx = requriedChannel.getTransaction();
        tx.begin();

        requriedChannel.put(event);

        tx.commit();
      } catch (ChannelException ex) {
        tx.rollback();
        throw ex;
      } finally {
        if (tx != null) {
          tx.close();
        }
      }
    }

    // Process optional channels
    List<Channel> optionalChannels = selector.getOptionalChannels(event);
    for (Channel optionalChannel : optionalChannels) {
      Transaction tx = null;
      try {
        tx = optionalChannel.getTransaction();
        tx.begin();

        optionalChannel.put(event);

        tx.commit();
      } catch (ChannelException ex) {
        tx.rollback();
        LOG.warn("Unable to put event on optional channel "
            + optionalChannel.getName(), ex);
      } finally {
        if (tx != null) {
          tx.close();
        }
      }
    }
  }
}
