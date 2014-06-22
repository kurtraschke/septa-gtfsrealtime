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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kurtraschke.septa.gtfsrealtime.model.Train;

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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Singleton;

@Singleton
public class TrainViewService {

    private Logger _log = LoggerFactory.getLogger(TrainViewService.class);
    private HttpClientConnectionManager _connectionManager;

    @PostConstruct
    public void start() {
        _connectionManager = new BasicHttpClientConnectionManager();
    }

    @PreDestroy
    public void stop() {
        _connectionManager.shutdown();
    }

    public Collection<Train> getTrains() throws URISyntaxException,
            ClientProtocolException, IOException {
        URIBuilder b = new URIBuilder(
                "http://www3.septa.org/hackathon/TrainView/");

        CloseableHttpClient client = HttpClients.custom().setConnectionManager(
                _connectionManager).build();

        HttpGet httpget = new HttpGet(b.build());
        try (CloseableHttpResponse response = client.execute(httpget);
                InputStream responseInputStream = response.getEntity().getContent();
                Reader responseEntityReader = new InputStreamReader(responseInputStream)) {
            JsonParser parser = new JsonParser();

            JsonArray trainObjects = (JsonArray) parser.parse(responseEntityReader);

            ArrayList<Train> allTrains = new ArrayList<>(trainObjects.size());

            for (JsonElement trainElement : trainObjects) {
                JsonObject trainObject = (JsonObject) trainElement;

                try {
                    allTrains.add(new Train(trainObject.get("lat").getAsDouble(),
                            trainObject.get("lon").getAsDouble(),
                            trainObject.get("trainno").getAsString(), trainObject.get(
                                    "service").getAsString(),
                            trainObject.get("dest").getAsString(),
                            trainObject.get("nextstop").getAsString(),
                            trainObject.get("late").getAsInt(),
                            trainObject.get("SOURCE").getAsString()));
                } catch (Exception e) {
                    _log.warn("Exception processing train JSON", e);
                    _log.warn(trainObject.toString());
                }
            }
            return allTrains;
        }
    }

    public static void main(String... args) throws ClientProtocolException,
            URISyntaxException, IOException {
        TrainViewService tvs = new TrainViewService();
        tvs.start();
        for (Train t : tvs.getTrains()) {
            System.out.println(t);
        }

        tvs.stop();
    }

}
