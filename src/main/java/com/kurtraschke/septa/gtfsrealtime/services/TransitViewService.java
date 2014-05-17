/*
 * Copyright (C) 2014 Kurt Raschke <kurt@kurtraschke.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.kurtraschke.septa.gtfsrealtime.services;

import com.google.common.collect.Iterables;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kurtraschke.septa.gtfsrealtime.model.Bus;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Singleton;

@Singleton
public class TransitViewService {

  private Logger _log = LoggerFactory.getLogger(TransitViewService.class);
  private HttpClientConnectionManager _connectionManager;

  @PostConstruct
  public void start() {
    _connectionManager = new BasicHttpClientConnectionManager();
  }

  @PreDestroy
  public void stop() {
    _connectionManager.shutdown();
  }

  public Collection<Bus> getBuses() throws URISyntaxException,
      ClientProtocolException, IOException {
    URIBuilder b = new URIBuilder(
        "http://www3.septa.org/hackathon/TransitViewAll/");

    CloseableHttpClient client = HttpClients.custom().setConnectionManager(
        _connectionManager).build();

    HttpGet httpget = new HttpGet(b.build());
    try (CloseableHttpResponse response = client.execute(httpget)) {
      HttpEntity entity = response.getEntity();

      JsonParser parser = new JsonParser();

      JsonObject o = (JsonObject) parser.parse(new InputStreamReader(
          entity.getContent()));

      JsonArray routes = (JsonArray) Iterables.getOnlyElement(o.entrySet()).getValue();

      List<Bus> allBuses = new ArrayList<>();

      for (JsonElement routeElement : routes) {
        JsonArray buses = ((JsonArray) (Iterables.getOnlyElement(((JsonObject) routeElement).entrySet()).getValue()));

        for (JsonElement busElement : buses) {
          JsonObject busObject = (JsonObject) busElement;

          try {
            allBuses.add(new Bus(busObject.get("lat").getAsDouble(),
                busObject.get("lng").getAsDouble(),
                busObject.get("label").getAsString(),
                busObject.get("VehicleID").getAsString(), busObject.get(
                    "BlockID").getAsString(),
                busObject.get("Direction").getAsString(),
                (!(busObject.get("destination") instanceof JsonNull))
                    ? busObject.get("destination").getAsString() : null,
                busObject.get("Offset").getAsInt()));
          } catch (Exception e) {
            _log.warn("Exception processing bus JSON", e);
            _log.warn(busObject.toString());
          }
        }
      }

      return allBuses;
    }
  }

  public static void main(String... args) throws ClientProtocolException,
      URISyntaxException, IOException {
    TransitViewService tvs = new TransitViewService();
    tvs.start();
    for (Bus b : tvs.getBuses()) {
      System.out.println(b);
    }

    tvs.stop();
  }
}
