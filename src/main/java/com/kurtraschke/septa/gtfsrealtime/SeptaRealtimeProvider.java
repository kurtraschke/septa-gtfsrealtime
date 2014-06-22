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

package com.kurtraschke.septa.gtfsrealtime;

import org.onebusaway.gtfs.impl.GtfsRelationalDaoImpl;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.serialization.GtfsReader;
import org.onebusaway.gtfs.services.GtfsRelationalDao;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeGuiceBindingTypes.Alerts;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeGuiceBindingTypes.TripUpdates;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeGuiceBindingTypes.VehiclePositions;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeIncrementalUpdate;
import org.onebusaway.gtfs_realtime.exporter.GtfsRealtimeSink;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.Position;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeEvent;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import com.kurtraschke.septa.gtfsrealtime.model.ActivatedTrip;
import com.kurtraschke.septa.gtfsrealtime.model.Bus;
import com.kurtraschke.septa.gtfsrealtime.model.Train;
import com.kurtraschke.septa.gtfsrealtime.services.BlockToTripMapperService;
import com.kurtraschke.septa.gtfsrealtime.services.TrainViewService;
import com.kurtraschke.septa.gtfsrealtime.services.TransitViewService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;

public class SeptaRealtimeProvider {

  private static final Logger _log = LoggerFactory.getLogger(SeptaRealtimeProvider.class);
  private ScheduledExecutorService _executor;
  private GtfsRealtimeSink _vehiclePositionsSink;
  private GtfsRealtimeSink _tripUpdatesSink;
  private GtfsRealtimeSink _alertsSink;
  private TransitViewService _transitViewService;
  private TrainViewService _trainViewService;

  private GtfsRelationalDao _busGtfsDao;
  private GtfsRelationalDao _railGtfsDao;

  private BlockToTripMapperService _busBlockMapper;
  private BlockToTripMapperService _railBlockMapper;

  private final HashMap<String, Calendar> _entityLastUpdate = new HashMap<>();

  private final String AGENCY_ID = "SEPTA";
  private final long EXPIRE_DATA_AFTER = 5 * 60 * 1000;

  @Inject
  @Named("refreshInterval.bus")
  private int _busRefreshInterval;

  @Inject
  @Named("refreshInterval.rail")
  private int _railRefreshInterval;

  @Inject
  @Named("gtfsPath.bus")
  private File _busGtfsPath;

  @Inject
  @Named("gtfsPath.rail")
  private File _railGtfsPath;

  @Inject
  public void setVehiclePositionsSink(@VehiclePositions
  GtfsRealtimeSink sink) {
    _vehiclePositionsSink = sink;
  }

  @Inject
  public void setTripUpdateSink(@TripUpdates
  GtfsRealtimeSink sink) {
    _tripUpdatesSink = sink;
  }

  @Inject
  public void setAlertsSink(@Alerts
  GtfsRealtimeSink sink) {
    _alertsSink = sink;
  }

  @Inject
  public void setTransitViewService(TransitViewService transitViewService) {
    _transitViewService = transitViewService;
  }

  @Inject
  public void setTrainViewService(TrainViewService trainViewService) {
    _trainViewService = trainViewService;
  }

  public SeptaRealtimeProvider() {

  }

  @PostConstruct
  public void start() {
    try {
      _log.info("Starting GTFS-realtime service");

      _busGtfsDao = loadGtfs(_busGtfsPath);
      _railGtfsDao = loadGtfs(_railGtfsPath);

      _busBlockMapper = new BlockToTripMapperService(_busGtfsDao);
      _railBlockMapper = new BlockToTripMapperService(_railGtfsDao);

      _executor = Executors.newSingleThreadScheduledExecutor();
      _executor.scheduleWithFixedDelay(new BusRefreshTask(), 0,
              _busRefreshInterval, TimeUnit.SECONDS);

      _executor.scheduleWithFixedDelay(new TrainRefreshTask(), 0,
              _railRefreshInterval, TimeUnit.SECONDS);

      _executor.scheduleWithFixedDelay(new ExpireDataTask(), 0, 1,
              TimeUnit.MINUTES);
    } catch (IOException ex) {
      _log.error("Exception while starting GTFS-realtime service", ex);
      throw new IllegalStateException(ex);
    }
  }

  @PreDestroy
  public void stop() {
    _log.info("Stopping GTFS-realtime service");
    _executor.shutdownNow();
  }

  private static GtfsRelationalDao loadGtfs(File gtfsPath) throws IOException {
    GtfsReader reader = new GtfsReader();
    GtfsRelationalDaoImpl dao = new GtfsRelationalDaoImpl();
    reader.setInputLocation(gtfsPath);
    reader.setEntityStore(dao);
    reader.run();
    return dao;
  }

  private TripDescriptor tripDescriptorForBlock(String blockId,
      Calendar blockActiveTime, BlockToTripMapperService blockMapper) {
    ActivatedTrip at = blockMapper.mapBlockToTrip(new AgencyAndId(AGENCY_ID,
        blockId), blockActiveTime, blockMapper.getAutoMaxLookBack());

    TripDescriptor.Builder tdb = TripDescriptor.newBuilder();

    tdb.setTripId(at.getTrip().getId().getId());
    tdb.setRouteId(at.getTrip().getRoute().getId().getId());

    tdb.setStartDate(String.format("%04d%02d%02d",
        at.getServiceDate().getYear(), at.getServiceDate().getMonth(),
        at.getServiceDate().getDay()));

    return tdb.build();

  }

  private StopTime firstStopTimeForTripId(String tripId, GtfsRelationalDao dao) {

    return dao.getStopTimesForTrip(
        dao.getTripForId(new AgencyAndId(AGENCY_ID, tripId))).get(0);
  }

  private Position positionForBus(Bus bus) {
    Position.Builder pb = Position.newBuilder();

    pb.setLatitude((float) bus.getLatitude());
    pb.setLongitude((float) bus.getLongitude());
    return pb.build();
  }

  private VehicleDescriptor vehicleDescriptorForBus(Bus bus) {
    VehicleDescriptor.Builder vdb = VehicleDescriptor.newBuilder();

    vdb.setId(bus.getVehicleId());
    vdb.setLabel(bus.getLabel());

    return vdb.build();
  }

  private void processBus(Bus bus, Calendar now) {
    TripDescriptor td;

    Calendar adjustedNow = (Calendar) now.clone();
    adjustedNow.add(Calendar.MINUTE, -1 * bus.getOffset());

    try {
      td = tripDescriptorForBlock(bus.getBlockId(), adjustedNow,
              _busBlockMapper);
    } catch (Exception e) {
      td = null;
    }

    VehicleDescriptor vd = vehicleDescriptorForBus(bus);
    Position pos = positionForBus(bus);

    VehiclePosition.Builder vp = VehiclePosition.newBuilder();

    if (td != null) {
      vp.setTrip(td);
    }

    vp.setVehicle(vd);
    vp.setTimestamp(adjustedNow.getTimeInMillis() / 1000L);
    vp.setPosition(pos);

    String entityId = "BUS" + bus.getVehicleId();

    pushEntity(entityId, _vehiclePositionsSink, vp.build(),
            FeedEntity.VEHICLE_FIELD_NUMBER);

    _entityLastUpdate.put(entityId, now);
  }

  private Position positionForTrain(Train train) {
    Position.Builder pb = Position.newBuilder();

    pb.setLatitude((float) train.getLatitude());
    pb.setLongitude((float) train.getLongitude());
    return pb.build();
  }

  private VehicleDescriptor vehicleDescriptorForTrain(Train train) {
    VehicleDescriptor.Builder vdb = VehicleDescriptor.newBuilder();

    vdb.setId(train.getTrainNumber());
    vdb.setLabel(train.getTrainNumber());

    return vdb.build();
  }

  private void processTrain(Train train, Calendar now) {
    TripDescriptor td;

    try {
      Calendar adjustedNow = (Calendar) now.clone();
      adjustedNow.add(Calendar.MINUTE, -1 * train.getLate());
      td = tripDescriptorForBlock(train.getTrainNumber(), adjustedNow,
          _railBlockMapper);
    } catch (Exception e) {
      td = null;
    }

    VehicleDescriptor vd = vehicleDescriptorForTrain(train);
    Position pos = positionForTrain(train);

    TripUpdate.Builder tu = TripUpdate.newBuilder();
    VehiclePosition.Builder vp = VehiclePosition.newBuilder();

    if (td != null) {
      vp.setTrip(td);
    }

    vp.setVehicle(vd);
    vp.setTimestamp(now.getTimeInMillis() / 1000L);
    vp.setPosition(pos);

    if (td != null) {
      tu.setTrip(td);
    }

    tu.setVehicle(vd);
    tu.setTimestamp(now.getTimeInMillis() / 1000L);

    if (td != null && train.getLate() != 999) {
      StopTimeUpdate.Builder stub = tu.addStopTimeUpdateBuilder();

      StopTime st = firstStopTimeForTripId(td.getTripId(), _railGtfsDao);

      stub.setStopId(st.getStop().getId().getId());
      stub.setStopSequence(st.getStopSequence());

      StopTimeEvent.Builder steb = stub.getDepartureBuilder();

      steb.setDelay(train.getLate() * 60);
    }

    String entityId = "TRAIN" + train.getTrainNumber();

    if (tu.isInitialized()) {
      pushEntity(entityId, _tripUpdatesSink, tu.build(),
          FeedEntity.TRIP_UPDATE_FIELD_NUMBER);
    }

    pushEntity(entityId, _vehiclePositionsSink, vp.build(),
        FeedEntity.VEHICLE_FIELD_NUMBER);

    _entityLastUpdate.put(entityId, now);
  }

  private void pushEntity(String id, GtfsRealtimeSink sink, Object value,
      int field) {
    GtfsRealtimeIncrementalUpdate griu = new GtfsRealtimeIncrementalUpdate();

    FeedEntity.Builder feb = FeedEntity.newBuilder();

    feb.setId(id);
    feb.setField(FeedEntity.getDescriptor().findFieldByNumber(field), value);

    griu.addUpdatedEntity(feb.build());

    sink.handleIncrementalUpdate(griu);
  }

  private class BusRefreshTask implements Runnable {
    @Override
    public void run() {
      try {
        _log.info("Refreshing buses");
        Collection<Bus> buses = _transitViewService.getBuses();

        Calendar now = Calendar.getInstance();

        for (Bus bus : buses) {
          _log.info("Processing bus {}", bus.getLabel());

          try {
            processBus(bus, now);
          } catch (Exception ex) {
            _log.warn("Exception while processing bus " + bus.getLabel(), ex);
          }

        }
      } catch (Exception ex) {
        _log.warn("Error in bus refresh task", ex);
      }
    }
  }

  private class TrainRefreshTask implements Runnable {
    @Override
    public void run() {
      try {
        _log.info("Refreshing trains");
        Collection<Train> trains = _trainViewService.getTrains();

        Calendar now = Calendar.getInstance();

        for (Train train : trains) {
          _log.info("Processing train {}", train.getTrainNumber());

          try {
            processTrain(train, now);
          } catch (Exception ex) {
            _log.warn(
                "Exception while processing train " + train.getTrainNumber(),
                ex);
          }

        }
      } catch (Exception ex) {
        _log.warn("Error in train refresh task", ex);
      }
    }
  }

  private class ExpireDataTask implements Runnable {
    @Override
    public void run() {
      Calendar now = Calendar.getInstance();
      for (Entry<String, Calendar> e : _entityLastUpdate.entrySet()) {
        String entityId = e.getKey();
        Calendar lastUpdate = e.getValue();

        long delta = now.getTimeInMillis() - lastUpdate.getTimeInMillis();

        if (delta > EXPIRE_DATA_AFTER) {
          GtfsRealtimeIncrementalUpdate griu = new GtfsRealtimeIncrementalUpdate();
          griu.addDeletedEntity(entityId);

          _tripUpdatesSink.handleIncrementalUpdate(griu);
          _vehiclePositionsSink.handleIncrementalUpdate(griu);
          _entityLastUpdate.remove(entityId);
        }
      }
    }
  }
}
