package com.marta.logistika.service.impl;

import com.marta.logistika.dao.api.RoadDao;
import com.marta.logistika.dto.RoadRecord;
import com.marta.logistika.entity.CityEntity;
import com.marta.logistika.entity.RoadEntity;
import com.marta.logistika.exception.checked.NoRouteFoundException;
import com.marta.logistika.exception.unchecked.EntityNotFoundException;
import com.marta.logistika.service.api.RoadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service("roadService")
public class RoadServiceImpl extends AbstractService implements RoadService {

    private final RoadDao roadDao;

    @Autowired
    public RoadServiceImpl(RoadDao roadDao) {
        this.roadDao = roadDao;
    }


    /**
     * Adds a new road
     *
     * @param road road to be added
     */
    @Override
    @Transactional
    public void add(RoadEntity road) {
        //check road parameters validity
        if (road == null) throw new IllegalArgumentException("Invalid input: null road");
        if (road.getFromCity() == null || road.getToCity() == null)
            throw new IllegalArgumentException("Invalid input: null road end point(s)");
        if (road.getFromCity().equals(road.getToCity()))
            throw new IllegalArgumentException("Invalid input: coinciding road end points");
        if (roadDao.getDirectRoadFromTo(road.getFromCity(), road.getToCity()) != null)
            throw new IllegalArgumentException(String.format("Invalid input: road between %s and %s already exists", road.getFromCity().getName(), road.getToCity().getName()));
        if (road.getDistance() < 1)
            throw new IllegalArgumentException(String.format("Invalid input: zero or negative road distance (%d)", road.getDistance()));

        //create return road
        RoadEntity returnRoad = new RoadEntity();
        returnRoad.setFromCity(road.getToCity());
        returnRoad.setToCity(road.getFromCity());
        returnRoad.setDistance(road.getDistance());

        //persist both roads
        roadDao.add(road);
        roadDao.add(returnRoad);
    }

    /**
     * Removes a road
     *
     * @param roadId road id
     */
    @Override
    @Transactional
    public void remove(long roadId) {
        //find requested road
        RoadEntity road = roadDao.findById(roadId);
        if (road == null) throw new EntityNotFoundException(roadId, RoadEntity.class);

        //find return road
        RoadEntity returnRoad = roadDao.getDirectRoadFromTo(road.getToCity(), road.getFromCity());

        //delete both roads
        roadDao.remove(road);
        roadDao.remove(returnRoad);
    }

    /**
     * Returns a list of all registered roads
     *
     * @return roads list
     */
    @Override
    public List<RoadRecord> listAll() {
        return roadDao.listAll()
                .stream()
                .map(r -> mapper.map(r, RoadRecord.class))
                .collect(Collectors.toList());
    }

    /**
     * Returns a list of all roads leading form a certain city
     *
     * @param city requested city
     * @return roads list
     */
    @Override
    public List<RoadRecord> listAllRoadsFrom(CityEntity city) {
        return roadDao.listAllRoadsFrom(city)
                .stream()
                .map(r -> mapper.map(r, RoadRecord.class))
                .collect(Collectors.toList());
    }

    @Override
    public List<CityEntity> listAllUnlinkedCities(CityEntity fromCity) {
        return roadDao.listAllUnlinkedCities(fromCity);
    }

    /**
     * Returns list of roads leading from one city to another (Dijkstra algorithm)
     *
     * @param fromCity starting point
     * @param toCity   end point
     * @return list of roads to reach the end point from the starting point
     */
    @Override
    public List<RoadRecord> findRouteFromTo(CityEntity fromCity, CityEntity toCity) throws NoRouteFoundException {
        // internal class for route calculation
        class Route {
            private LinkedList<RoadEntity> roadList;
            private int distance;

            private Route() {
                roadList = new LinkedList<>();
                distance = 0;
            }

            private Route(Route route) {
                this.roadList = new LinkedList<>(route.roadList);
                this.distance = route.distance;
            }

            private Route addRoad(RoadEntity road) {
                roadList.addFirst(road);
                distance += road.getDistance();
                return this;
            }

            @Override
            public String toString() {
                return "Route{" +
                        "roadList=" + Arrays.deepToString(roadList.toArray()) +
                        ", distance=" + distance +
                        '}';
            }
        }

        Map<CityEntity, Route> routesFound = new HashMap<>();
        routesFound.put(toCity, new Route());

        LinkedList<CityEntity> wave = new LinkedList<>();
        wave.add(toCity);

        while (!wave.isEmpty()) {

            CityEntity currentCity = wave.removeFirst();
            Route currentCityRoute = routesFound.get(currentCity);

            roadDao.listAllRoadsTo(currentCity).forEach(nextRoad -> {

                CityEntity nextCity = nextRoad.getFromCity();
                Route nextCityRoute = new Route(currentCityRoute).addRoad(nextRoad);

                if (!routesFound.containsKey(nextCity)) {
                    routesFound.put(nextCity, nextCityRoute);
                    if (!nextCity.equals(fromCity)) wave.add(nextCity);
                } else if (nextCityRoute.distance < routesFound.get(nextCity).distance) {
                    routesFound.put(nextCity, nextCityRoute);
                }
            });
        }

        if (!routesFound.containsKey(fromCity)) throw new NoRouteFoundException(fromCity, toCity);

        return routesFound.get(fromCity).roadList
                .stream()
                .map(r -> mapper.map(r, RoadRecord.class))
                .collect(Collectors.toList());

    }

    /**
     * Calculates the distance between two cities
     *
     * @param fromCity starting point
     * @param toCity   end point
     * @return distance
     * @throws NoRouteFoundException in case cities are not linked with roads
     */
    @Override
    public int getDistanceFromTo(CityEntity fromCity, CityEntity toCity) throws NoRouteFoundException {
        if (fromCity.equals(toCity)) return 0;
        return findRouteFromTo(fromCity, toCity)
                .stream()
                .mapToInt(RoadRecord::getDistance)
                .sum();
    }

    /**
     * Calculates the length of route given as a list of cities to be visited
     *
     * @param route list of cities to be visited
     * @return distance
     * @throws NoRouteFoundException in case any of cities is not reachable using registered roads
     */
    @Override
    public int getRouteDistance(List<CityEntity> route) throws NoRouteFoundException {
        int distance = 0;
        for (int i = 1; i < route.size(); i++) {
            distance += getDistanceFromTo(route.get(i), route.get(i - 1));
        }
        return distance;
    }
}
