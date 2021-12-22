/*
 * Copyright 2018 Carlos Guerrero, Isaac Lera.
 *
 * Created on Nov 09 08:10:55 2018
 * @authors:
 *     Carlos Guerrero
 *     carlos ( dot ) guerrero  uib ( dot ) es
 *     Isaac Lera
 *     isaac ( dot ) lera  uib ( dot ) es
 *
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 * This extension has been implemented for the research presented in the
 * article "A lightweight decentralized service placement policy for
 * performance optimization in fog computing", accepted for publication
 * in "Journal of Ambient Intelligence and Humanized Computing".
 */


package org.fog.placement;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppEdge;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.application.selectivity.SelectivityModel;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class ModulePlacementPSO extends ModulePlacement {
    public int numOfMigrations = 0;
    public int numOfCloudPlacements = 0;
    public double timeUsed = 0;
    protected ModuleMapping moduleMapping;
    protected List<Sensor> sensors;  // List of sensors
    protected List<Actuator> actuators;  //List of actuators
    protected HashMap<String, HashMap<String, Double>> mapModRateValues = new HashMap<String, HashMap<String, Double>>();  //Calculated value for the request rate that arrives to each module from each single (1.0) request to each incoming Tuple from a sensor
    protected HashMap<String, HashMap<String, Double>> mapEdgeRateValues = new HashMap<String, HashMap<String, Double>>();  //Calculated value for the request rate that arrives to each Tuple from each single (1.0) request to each app-incoming Tuple from a sensor
    protected HashMap<String, HashMap<String, Double>> mapDeviceSensorRate = new HashMap<String, HashMap<String, Double>>();  //Calculated value for the request rate that arrives to each device adding all the sensor rates that are lower to them
    protected Set<FogDevice> gateways = new HashSet<FogDevice>();  //List of the leaf devices where the mobile devices are connecte
    protected Map<FogDevice, Double> currentCpuLoad = new HashMap<FogDevice, Double>();  //Load of the cpu of each device in MIPS
    protected List<Pair<FogDevice, String>> modulesToPlace = new ArrayList<Pair<FogDevice, String>>();  // SAR (Service Allocation Requests) that are stil pending to be analyzed and decide to be placed
    protected Map<FogDevice, List<String>> currentModuleMap = new HashMap<FogDevice, List<String>>();  //Preallocated list of pairs device-module
    protected List<String> moduleOrder = new ArrayList<String>();  //Order of the modules of the app that need to be respected to accomplish the consumption relationships
    protected Map<String, List<String>> mapModuleClosure = new HashMap<String, List<String>>();  //The closure of each element in the app graph
    //number of Fog Device
    int m;
    //number of App Modules to place
    int n;

    int numberOfParticles = 100;
    int numberOfIterations = 50;
    Particle[] swarm;
    Random ran = new Random();


    //public Integer aaaa() {
    //	return numOfMigrations;
    //}

//---------------------------------------------------------------------------------------------------------------------------
//---------------------------------------------------------------------------------------------------------------------------

    public ModulePlacementPSO(List<FogDevice> fogDevices,
                              List<Sensor> sensors,
                              List<Actuator> actuators,
                              Application application,
                              ModuleMapping moduleMapping,
                              Integer[] subAppsRate,
                              String resultsFN,
                              Integer countErrors) {

        //fogDevices.remove(0);

        this.setFogDevices(fogDevices);
        this.setApplication(application);
        this.setModuleToDeviceMap(new HashMap<String, List<Integer>>());
        this.setDeviceToModuleMap(new HashMap<Integer, List<AppModule>>());
        this.sensors = sensors;
        this.moduleMapping = moduleMapping;
//        this.setApplication(application);

        for (FogDevice dev : getFogDevices()) {
            currentCpuLoad.put(dev, 0.0);
            currentModuleMap.put(dev, new ArrayList<String>());
        }

        searchGateways();//Check the parent devices
        calculateEdgeRate();
        calculateModRate();
        calculateDeviceSensorDependencies();
        calculateModuleOrder();
        calculateClosure();
        mapModulesPreviouslyMapped();
        mapModules();
        //calculate_hop_count();

        //System.out.println("NUMERO de migrations:"+numOfMigrations);
        //System.out.println("NUMERO de cloud placements:"+numOfCloudPlacements);

        try {

            File archivoCPU = new File("./CPUpop" + resultsFN + ".csv");
            File archivoRAT = new File("./RATpop" + resultsFN + ".csv");
            File archivoTIME = new File("./TIMEpop" + resultsFN + ".csv");
            BufferedWriter bwCPU;
            BufferedWriter bwRAT;
            BufferedWriter bwTIME;

            bwCPU = new BufferedWriter(new FileWriter(archivoCPU));

            bwRAT = new BufferedWriter(new FileWriter(archivoRAT));

            bwTIME = new BufferedWriter(new FileWriter(archivoTIME, true));

            bwCPU.write("hopcount;cpuusage;cputotal;numservices\n");
            bwRAT.write("hopcount;requestratio\n");
            //bwTIME.write("hopcount;requestratio\n");

            bwTIME.write(this.timeUsed + "\n");


            Integer maxLevel = 0;
            for (FogDevice dev : getFogDevices()) {
                maxLevel = Math.max(dev.getLevel(), maxLevel);
            }

            for (FogDevice dev : getFogDevices()) {
                Integer hopcountOF = maxLevel - dev.getLevel();
                Double cpuusageOF = currentCpuLoad.get(dev);
                Integer cputotalOF = dev.getHost().getTotalMips();
                Integer numservicesOF = currentModuleMap.get(dev).size();
                if (hopcountOF > 0) {
                    bwCPU.write(hopcountOF + ";" + cpuusageOF + ";" + cputotalOF + ";" + numservicesOF + "\n");
                }


            }

            for (FogDevice dev : getFogDevices()) {
                List<String> allocatedModules = currentModuleMap.get(dev);
                for (String moduleAlloc : allocatedModules) {
                    Integer appId = Integer.parseInt(moduleAlloc.substring(moduleAlloc.length() - 1));
                    //double moduleRateOF = calculateModuleRate(dev, moduleAlloc);

                    Integer hopcountOF = maxLevel - dev.getLevel();
                    Integer requestratioOF = subAppsRate[appId];
					/*if ( hopcountOF > 0 ) {
						bwRAT.write(hopcountOF+";"+requestratioOF+";"+moduleRateOF+"\n");
					}*/

                }
            }

            bwCPU.close();
            bwRAT.close();
            bwTIME.close();

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }


    }

    protected void mapModulesPreviouslyMapped() {
        for (String deviceName : moduleMapping.getModuleMapping().keySet()) {
            for (String moduleName : moduleMapping.getModuleMapping().get(deviceName)) {
                int deviceId = CloudSim.getEntityId(deviceName);
                PreAllocate(getFogDeviceById(deviceId), moduleName);
            }
        }
    }

    protected void PreAllocate(FogDevice dev, String mod) {
        List<String> currentModules;
        if ((currentModules = currentModuleMap.get(dev)) != null) {
            currentModules.add(mod);
        } else {
            currentModules = new ArrayList<String>();
            currentModules.add(mod);
            currentModuleMap.put(dev, currentModules);
        }
    }

    protected void atToPendingList(Pair<FogDevice, String> pair) {
        if (!modulesToPlace.contains(pair)) {
            modulesToPlace.add(pair);
        }
    }

    protected void Placement() {
        int numOptExecutions = 0;
        //System.out.println("Test 8.1");
        //this.getFogDevices().remove(0);
        //System.out.println("Modules to place: " + modulesToPlace.size());
        //System.out.println("Fog devices number: " + this.getFogDevices().size());

        /*-------------------------------------------------------------------------------------------------------------------*/
        /*-------------------------------------------PSO---------------------------------------------------------------------*/
        /*-------------------------------------------------------------------------------------------------------------------*/
        //this.getFogDevices().remove(0);
        int m_fog_devices = this.getFogDevices().size();
        int n_modules = modulesToPlace.size();

        n = n_modules;
        m = m_fog_devices;

        List<AppModule> mod = this.getApplication().getModules();
        List<FogDevice> fog = this.getFogDevices();

        //System.out.println("m_fog_devices "+ m_fog_devices);
        //System.out.println("n_modules "+ n_modules);

        ArrayList<double[]> runTime = new ArrayList<double[]>();
        ArrayList<double[]> usageRAM = new ArrayList<double[]>();

        //will calculate the execution time each module takes if it runs on one of the VMs
        for (int i = 0; i < m_fog_devices; i++) {

            //FogDevice fogdevice =
            //System.out.println(this.getFogDevices().get(i).getRatePerMips() );
            //System.out.println("Host name: "+this.getFogDevices().get(i).getHost());
            //System.out.println("TotalMips: "+this.getFogDevices().get(i).getHost().getTotalMips());
            //System.out.println(this.getFogDevices().get(i).getName());
            double[] arr = new double[n_modules];
            double[] ram = new double[n_modules];
            int appModSize = this.getApplication().getModules().size();

            for (int j = 0; j < n_modules; j++) {
                //System.out.println(j);
                Pair<FogDevice, String> module_i = modulesToPlace.get(j);
                //System.out.println(module_i.getValue());
                //System.out.println(module_i.getKey());
                //System.out.println(module_i.getFirst());
                //System.out.println(module_i.getSecond());
                //System.out.println(module_i.getFirst().getName());
                //System.out.println("+++++++++++++");
                //System.out.println("Module Name:"+this.getApplication().getModules().get(j).getName());
                //System.out.println("Fog Capacity:"+this.getFogDevices().get(i).getHost().getTotalMips());
                //System.out.println("Fog name:"+this.getFogDevices().get(i).getName());
                //System.out.println("Module MIPS requirement:"+this.getApplication().getModules().get(j).getMips());

                Double procTime3 = this.getApplication().getModules().get(j % appModSize).getMips();
                //System.out.println("procTime3"+procTime3);
                Double procTime2 = (double) this.getFogDevices().get(i).getHost().getTotalMips();
                //System.out.println("procTime2"+procTime2);
                Double procTime = procTime3 / procTime2;
                Double networkTime = this.getFogDevices().get(i).getUplinkLatency();
                //System.out.println("Module Network Time:"+(procTime+networkTime));

                arr[j] = procTime + networkTime;
                ram[j] = this.getFogDevices().get(i).getHost().getRam();
                //System.out.println(arr[j] );
                //System.out.println(ram[j] );
            }

            runTime.add(arr);
            usageRAM.add(ram);
        }

        swarm = new Particle[numberOfParticles];

        ArrayList<int[]> bestGlobalPositions = new ArrayList<int[]>();// the best positions found

        double bestGlobalFitness = Double.MAX_VALUE; // smaller values better

        // +++++++++++++++++++++++>

        //System.out.println("swarm:"+swarm.length);

        for (int l = 0; l < swarm.length; ++l) // initialize each Particle in the swarm
        {
            //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
            //+( positions )++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
            //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

            ArrayList<int[]> initPositions = new ArrayList<int[]>();
            int[] assignedTasksArray = new int[n_modules];
            //System.out.println(assignedTasksArray.toString());
            for (int i = 0; i < m_fog_devices; i++) {

                int[] randomPositions = new int[n_modules];

                for (int j = 0; j < n_modules; j++) {

                    // if not assigned assign it
                    if (assignedTasksArray[j] == 0) {
                        randomPositions[j] = ran.nextInt(2);

                        if (randomPositions[j] == 1) {
                            assignedTasksArray[j] = 1;
                        }
                    } else {
                        randomPositions[j] = 0;
                    }

                }

                initPositions.add(randomPositions);
            }

            // to assign unassigned tasks
            ArrayList<int[]> newPositionsMatrix = checkResourceAssignmentForNonAssignedTasks(initPositions, assignedTasksArray);


            double fitness = ObjectiveFunction(runTime, usageRAM, newPositionsMatrix);

            //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
            //+( Velocity )+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
            //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

            ArrayList<double[]> initVelocities = new ArrayList<double[]>();
            int[] assignedTasksArrayInVelocityMatrix = new int[n_modules];

            for (int i = 0; i < m_fog_devices; i++) {

                double[] randomPositions = new double[n_modules];

                for (int j = 0; j < n_modules; j++) {

                    // if not assigned assign it
                    if (assignedTasksArrayInVelocityMatrix[j] == 0) {
                        randomPositions[j] = ran.nextInt(2);

                        if (randomPositions[j] == 1) {
                            assignedTasksArrayInVelocityMatrix[j] = 1;
                        }
                    } else {
                        randomPositions[j] = 0;
                    }
                }

                initVelocities.add(randomPositions);
            }

            swarm[l] = new Particle(newPositionsMatrix, fitness, initVelocities, newPositionsMatrix, fitness);

            // does current Particle have global best position/solution?
            if (swarm[l].fitness < bestGlobalFitness) {
                bestGlobalFitness = swarm[l].fitness;

                bestGlobalPositions = swarm[l].positionsMatrix;
            }
        }

        double c1 = 1.49445; // cognitive/local weight
        double c2 = 1.49445; // social/global weight
        int r1, r2; // cognitive and social randomizations

        //minimum and maximum values to have since we are working with only two values 0 and 1
        int minV = 0;
        int maxV = 1;

        //to keep an array of the average fitness per particle
        ArrayList<double[]> averageFitnesses = new ArrayList<double[]>();

        //fill the averageFitnesses with empty arrays
        for (int i = 0; i < swarm.length; i++) {
            averageFitnesses.add(new double[numberOfIterations]);
        }

        for (int iter = 0; iter < numberOfIterations; iter++) {

            for (int l = 0; l < swarm.length; ++l) {

                //calculate InertiaValue
                double w = InertiaValue(l, iter, averageFitnesses);

                ArrayList<double[]> newVelocitiesMatrix = new ArrayList<double[]>();
                ArrayList<int[]> newPositionsMatrix = new ArrayList<int[]>();
                double newFitness;

                Particle currParticle = swarm[l];

                //to keep track of the zeros and ones in the arrayList per task
                int[] assignedTasksArrayInVelocityMatrix = new int[n_modules];

                for (int i = 0; i < currParticle.velocitiesMatrix.size(); i++) {
                    double[] vmVelocities = currParticle.velocitiesMatrix.get(i);
                    int[] vmBestPositions = currParticle.bestPositionsMatrix.get(i);
                    int[] vmPostitons = currParticle.positionsMatrix.get(i);
                    int[] vmGlobalbestPositions = bestGlobalPositions.get(i);
                    double[] newVelocities = new double[n_modules];

                    //length - 1 => v(t+1) = w*v(t) ..
                    for (int j = 0; j < vmVelocities.length - 1; j++) {
                        r1 = ran.nextInt(2);
                        r2 = ran.nextInt(2);
                        if (assignedTasksArrayInVelocityMatrix[j] == 0) {
                            //velocity vector
                            newVelocities[j] = (w * vmVelocities[j + 1] + c1 * r1 * (vmBestPositions[j] - vmPostitons[j]) + c2 * r2 * (vmGlobalbestPositions[j] - vmPostitons[j]));
                            if (newVelocities[j] < minV) {
                                newVelocities[j] = minV;
                            } else if (newVelocities[j] > maxV) {
                                newVelocities[j] = maxV;
                            }
                            if (newVelocities[j] == 1) {
                                assignedTasksArrayInVelocityMatrix[j] = 1;
                            }
                        } else {
                            newVelocities[j] = 0;
                        }
                    }
                    //add the new velocities into the arrayList
                    newVelocitiesMatrix.add(newVelocities);
                }
                currParticle.velocitiesMatrix = newVelocitiesMatrix;
                //----->>>>> Done with velocities
                //to keep track of the zeros and ones in the arrayList per task
                int[] assignedTasksArrayInPositionsMatrix = new int[n_modules];
                for (int i = 0; i < currParticle.velocitiesMatrix.size(); i++) {
                    double[] vmVelocities = currParticle.velocitiesMatrix.get(i);
                    int[] newPosition = new int[n_modules];
                    //length - 1 => v(t+1) = w*v(t) ..
                    for (int j = 0; j < vmVelocities.length - 1; j++) {
                        int random = ran.nextInt(2);
                        if (assignedTasksArrayInPositionsMatrix[j] == 0) {
                            //to calculate sigmoid function
                            double sig = 1 / (1 + Math.exp(-vmVelocities[j]));

                            if (sig > random) {
                                newPosition[j] = 1;
                            } else {
                                newPosition[j] = 0;
                            }

                            if (newPosition[j] == 1) {
                                assignedTasksArrayInPositionsMatrix[j] = 1;
                            }
                        } else {
                            newPosition[j] = 0;
                        }
                    }

                    //add the new velocities into the arrayList
                    newPositionsMatrix.add(newPosition);
                }

                //will check for non assigned tasks
                newPositionsMatrix = checkResourceAssignmentForNonAssignedTasks(newPositionsMatrix, assignedTasksArrayInPositionsMatrix);
				/*System.out.println(Arrays.toString(usageRAM.get(0)));
				System.out.println(Arrays.toString(usageRAM.get(1)));
				System.out.println(Arrays.toString(usageRAM.get(2)));
				System.out.println(Arrays.toString(usageRAM.get(3)));
				System.out.println(Arrays.toString(usageRAM.get(4)));
				System.out.println(Arrays.toString(usageRAM.get(5)));

				System.out.println(Arrays.toString(runTime.get(0)));
				System.out.println(Arrays.toString(runTime.get(1)));
				System.out.println(Arrays.toString(runTime.get(2)));
				System.out.println(Arrays.toString(runTime.get(3)));
				System.out.println(Arrays.toString(runTime.get(4)));
				System.out.println(Arrays.toString(runTime.get(5)));*/
//				newPositionsMatrix = ReBalancePSO(newPositionsMatrix, runTime);
                currParticle.positionsMatrix = newPositionsMatrix;

                //-------> done with new positions
                newFitness = ObjectiveFunction(runTime, usageRAM, newPositionsMatrix);
                currParticle.fitness = newFitness;

                //if the new fitness is better than what we already found
                //-> set the new fitness as the best fitness so far
                if (newFitness < currParticle.bestFitness) {
                    currParticle.bestPositionsMatrix = newPositionsMatrix;
                    currParticle.bestFitness = newFitness;
                }

                //if the new fitness is better than all solutions found by all particles
                //-> set the new fitness as the global fitness
                if (newFitness < bestGlobalFitness) {
                    bestGlobalPositions = newPositionsMatrix;
                    bestGlobalFitness = newFitness;
                }

                //to add the new fitness to the average fitness array
                double[] fitnessArrayInAvgFitnesses = averageFitnesses.get(l);
                fitnessArrayInAvgFitnesses[iter] = newFitness;
                //Added .. worse solutions
                averageFitnesses.set(l, fitnessArrayInAvgFitnesses);
            }
        }

        System.out.println("Best Global: ");
        for (int i = 0; i < bestGlobalPositions.size(); i++) {
            int[] fogNodeModules = bestGlobalPositions.get(i);
            System.out.println("Fog node " + i);
            System.out.println(Arrays.toString(fogNodeModules));
        }

        currentModuleMap.clear();

        List<AppModule> modules = getApplication().getModules();

        for (int i = 0; i < this.getFogDevices().size(); i++) {
            FogDevice dev = this.getFogDevices().get(i);
            //System.out.println("fogDevice: "+dev.toString());
            String values = Arrays.toString(bestGlobalPositions.get(i));
            //System.out.println(Arrays.toString( bestGlobalPositions.get(i)));
            int[] contentFromList = bestGlobalPositions.get(i);
            List<String> place = new ArrayList<String>();
            for (int j = 0; j < modules.size(); j++) {
                int binaryValue = contentFromList[j];
                //System.out.println(modules.get(j).getName());
                //System.out.println("Value at index - "+j+" is :"+binaryValue);
                if (binaryValue == 1) {
                    place.add(modules.get(j).getName());
                    //currentModuleMap.put(dev,getApplication().getModuleByName(modules.get(j).getName()));
                    //modulesToPlace.remove(j);
                }
            }
            //System.out.println(place.size());
            currentModuleMap.put(dev, place);
            //modulesToPlace. = modulesToPlace.size() -place.size();

            for (int counter = 0; counter < modulesToPlace.size(); counter++) {
                Pair<FogDevice, String> pair = modulesToPlace.get(counter);
                //System.out.println("pair.second ModName: "+pair.getSecond());
            }

            //int[] contentFromList = bestGlobalPositions.get(0);
			/*for (int i = 0; i < contentFromList.length; i++) {
				int j = contentFromList[i];
				if (j == 1) {
					System.out.println("pair.first Dev: " + dev);
				}
			}*/

            //System.out.println(values.length());
            //int[] contentFromList = bestGlobalPositions.get(0);
        }

		/*for (int counter = 0; counter < modulesToPlace.size(); counter++) {
			Pair<FogDevice, String> pair = modulesToPlace.get(counter);
			FogDevice dev = pair.getFirst();
			String modName = pair.getSecond();
			//System.out.println("pair.first Dev: "+dev);
			//System.out.println("pair.second ModName: "+modName);

		}*/
        /*--------------------------place the applications based on the results---------------------------------------------*/
        for (int counter = 0; counter < bestGlobalPositions.size(); counter++) {
            String values = Arrays.toString(bestGlobalPositions.get(counter));
            FogDevice dev = this.getFogDevices().get(counter);
            //System.out.println(Arrays.toString(bestGlobalPositions.get(counter)));
            //System.out.println("length:"+values.length());
            List<String> currentModules = currentModuleMap.get(dev);
            int[] contentFromList = bestGlobalPositions.get(counter);
            for (int i = 0; i < contentFromList.length; i++) {
                int j = contentFromList[i];
                Pair<FogDevice, String> pair = modulesToPlace.get(i);
                if (j == 1) {
                    //System.out.println("Value at index - "+i+" is :"+j);
                    //FogDevice dev = pair.getFirst();
                    String modName = pair.getSecond();
                    //System.out.println("pair.first Dev: " + dev);
                    //System.out.println("pair.second ModName: " + modName);
                    //currentModuleMap.put(dev,new ArrayList<String>());
                    if (currentModules == null) {
                        currentModules = new ArrayList<String>();
                        currentModuleMap.put(dev, currentModules);
                        PreAllocate(dev, modName);
                    } else {
                        currentModuleMap.put(dev, currentModules);
                    }

                }
            }

			/*//System.out.println("pair.first Dev: "+dev);
			int[] contentFromList = bestGlobalPositions.get(counter);
			for (int i = 0; i < contentFromList.length; i++) {
				int j = contentFromList[i];
			//	System.out.println("Value at index - "+i+" is :"+j);
				Pair<FogDevice, String> pair = modulesToPlace.get(i);
				FogDevice dev = pair.getFirst();
				String modName = pair.getSecond();
				//System.out.println("pair.first Dev: "+dev);
				//System.out.println("pair.second ModName: "+modName);
				if (j==1) {
					System.out.println("entro");
					List<String> currentModules = currentModuleMap.get(dev);
					System.out.println(dev);
					currentModuleMap.put(dev,new ArrayList<String>());
					if (currentModules == null) {
						currentModules = new ArrayList<String>();
						currentModuleMap.put(dev, currentModules);
						PreAllocate(dev,modName);
					}

				}

			}*/

            //FogDevice dev = pair.getFirst();
            //String modName = pair.getSecond();
            //System.out.println("pair.first Dev: "+dev);
            //System.out.println("pair.second ModName: "+modName);
//			modulesToPlace.get(counter);
/*			for (int i = 0; i < contentFromList.length; i++) {
				int j = contentFromList[i];
				//System.out.println("Value at index - "+i+" is :"+j);

				if (j==1) {
					//currentModuleMap.put(dev, currentModules);
					//System.out.println("pair.first Dev: "+dev);
					//System.out.println("pair.second ModName: "+modName);
					//System.out.println("ModuleMapping: "+moduleMapping.getModuleMapping());
					List<String> currentModules = currentModuleMap.get(dev);
					currentModuleMap.put(dev,new ArrayList<String>());
					//System.out.println("currentModules:"+currentModules);
					if (currentModules == null) {
						currentModules = new ArrayList<String>();
						currentModuleMap.put(dev, currentModules);
						PreAllocate(dev,modName);

					//	currentCpuLoad.put(dev, currentMips + requiredResources);
					}
				}
*/


        }

        //------------------------------------------------------------------------------------------------------------------------
/*			for (int counter = 0; counter < bestGlobalPositions.size(); counter++) {
				//System.out.println(Arrays.toString( bestGlobalPositions.get(counter)));
				String values = Arrays.toString( bestGlobalPositions.get(counter));
				//System.out.println(values.length());
				int[] contentFromList = bestGlobalPositions.get(counter);
				Pair<FogDevice, String> pair = modulesToPlace.get(counter);
				FogDevice dev = pair.getFirst();
				String modName = pair.getSecond();
				System.out.println("pair.first Dev: "+dev);
				System.out.println("pair.second ModName: "+modName);
				modulesToPlace.get(counter);
				for (int i = 0; i < contentFromList.length; i++) {
					int j = contentFromList[i];
					//System.out.println("Value at index - "+i+" is :"+j);

					if (j==1) {
						//currentModuleMap.put(dev, currentModules);
						//System.out.println("pair.first Dev: "+dev);
						//System.out.println("pair.second ModName: "+modName);
						//System.out.println("ModuleMapping: "+moduleMapping.getModuleMapping());
						List<String> currentModules = currentModuleMap.get(dev);
						currentModuleMap.put(dev,new ArrayList<String>());
						//System.out.println("currentModules:"+currentModules);
						if (currentModules == null) {
							currentModules = new ArrayList<String>();
							currentModuleMap.put(dev, currentModules);
							PreAllocate(dev,modName);

							//	currentCpuLoad.put(dev, currentMips + requiredResources);
						}
					}



				}
*/
        //---------------------------------------------------------------------------------------------------------------

        //	List<Stri1ng> currentModules = currentModuleMap.get(dev);
        //	if (currentModules == null) {
        //		currentModules = new ArrayList<String>();
        //		currentModuleMap.put(dev, currentModules);
        //	}

        //System.out.println(bestGlobalPositions.get(counter));

        //System.out.println(bestGlobalPositions.);
        //}

        /*-------------------------------------------------------------------------------------------------------------------*/
        /*-------------------------------------------PSO---------------------------------------------------------------------*/
        /*-------------------------------------------------------------------------------------------------------------------*/
    }


    @Override
    protected void mapModules() {
        for (FogDevice gw : gateways) {
            for (String modName : moduleOrder) {
                Pair<FogDevice, String> pair = new Pair<FogDevice, String>(gw, modName);
                atToPendingList(pair);
            }
        }
		/*Instant instant = Instant.now (); // Current date-time in UTC.
		String output = instant.toString ();
		System.out.println("Inicio:"+output);*/
        long start_time = System.nanoTime();
        Placement();
        long end_time = System.nanoTime();
        double difference = (end_time - start_time) / 1e6;
        this.timeUsed = difference;
        //System.out.println(difference);
		/*instant = Instant.now (); // Current date-time in UTC.
		output = instant.toString ();*/
        //System.out.println("Final:"+output);


        //resp = GeoLocationService.getLocationByIp(ipAddress);


        for (FogDevice dev : currentModuleMap.keySet()) {
            //	System.out.println("dev"+dev);
            for (String module : currentModuleMap.get(dev)) {
                //System.out.println(dev);
                //System.out.println(module);
                //System.out.println(countErrors);

                createModuleInstanceOnDevice(getApplication().getModuleByName(module), dev);
            }
        }
    }

    protected void searchGateways() {
        for (FogDevice device : getFogDevices()) {
            if (device.getName().startsWith("m-")) {
                gateways.add(getFogDeviceById(device.getParentId()));
            }
        }
    }

    protected void calculateClosure() {
        for (AppModule module : getApplication().getModules()) {
            List<String> moduleClosure = new ArrayList<String>();
            addRecursiveChildren(module.getName(), moduleClosure);
            Collections.reverse(moduleClosure);
            mapModuleClosure.put(module.getName(), moduleClosure);
        }
    }

    protected void addRecursiveChildren(String moduleName, List<String> mylist) {
        for (AppEdge edge : getApplication().getEdges()) {
            if (edge.getSource().equals(moduleName)) {
                if (!mylist.contains(moduleName)) {
                    addRecursiveChildren(edge.getDestination(), mylist);
                }
            }
        }
        if (!mylist.contains(moduleName)) {
            mylist.add(moduleName);
        }
    }

    protected void calculateModuleOrder() {
        for (AppEdge edge : getApplication().getEdges()) {
            if (edge.getEdgeType() == AppEdge.SENSOR) {
                addRecursiveChildren(edge.getDestination(), moduleOrder);
            }
        }
    }

    protected void pathEdgeRate(AppEdge pathEdgeInput, AppEdge currentEdge, double currentRate) {
        AppModule currentModule = getApplication().getModuleByName(currentEdge.getDestination());
        for (AppEdge edge : getApplication().getEdges()) {
            if (edge.getSource().equals(currentModule.getName()) && edge.getEdgeType() != AppEdge.ACTUATOR) {
                SelectivityModel s = currentModule.getSelectivityMap().get(new Pair<String, String>(currentEdge.getTupleType(), edge.getTupleType()));
                if (s != null) {
                    double newRate = currentRate * s.getMeanRate();
                    HashMap<String, Double> mapTargetEdge = null;
                    if ((mapTargetEdge = mapEdgeRateValues.get(edge.getTupleType())) != null) {
                        Double finalvalue;
                        if ((finalvalue = mapTargetEdge.get(pathEdgeInput.getTupleType())) != null) {
                            finalvalue += newRate;
                            mapTargetEdge.put(pathEdgeInput.getTupleType(), finalvalue);
                        } else {
                            mapTargetEdge.put(pathEdgeInput.getTupleType(), newRate);
                        }
                    } else {

                        mapTargetEdge = new HashMap<String, Double>();
                        mapTargetEdge.put(pathEdgeInput.getTupleType(), newRate);
                        mapEdgeRateValues.put(edge.getTupleType(), mapTargetEdge);
                    }
                    pathEdgeRate(pathEdgeInput, edge, newRate);
                }
            }
        }
    }

    protected void pathModRate(AppModule pathModuleInput, AppModule currentModule, double currentRate, String incomingTupleType, String initialTupleType) {
        for (AppEdge edge : getApplication().getEdges()) {
            if (edge.getSource().equals(currentModule.getName()) && edge.getEdgeType() != AppEdge.ACTUATOR) {

//				System.out.println("Current:"+currentModule.getName());
//				System.out.print(edge.getTupleType()+"::::");
//				System.out.print(edge.getSource());
//				System.out.print("====>");
//				System.out.println(edge.getDestination());
                AppModule destinationModule = getApplication().getModuleByName(edge.getDestination());
                String outgoingTupleType = edge.getTupleType();
                SelectivityModel s = currentModule.getSelectivityMap().get(new Pair<String, String>(incomingTupleType, outgoingTupleType));
                if (s != null) {
//					System.out.print(pathModuleInput.getName());
//					System.out.print("====>");
//					System.out.println(destinationModule.getName());

                    double newRate = currentRate * s.getMeanRate();
                    HashMap<String, Double> mapTargetModule = null;
                    if ((mapTargetModule = mapModRateValues.get(destinationModule.getName())) != null) {
                        Double finalvalue;
                        if ((finalvalue = mapTargetModule.get(initialTupleType)) != null) {
                            finalvalue += newRate;
                            mapTargetModule.put(initialTupleType, finalvalue);
                        } else {
                            mapTargetModule.put(initialTupleType, newRate);
                        }
                    } else {
                        mapTargetModule = new HashMap<String, Double>();
                        mapTargetModule.put(initialTupleType, newRate);
                        mapModRateValues.put(destinationModule.getName(), mapTargetModule);
                    }
                    pathModRate(pathModuleInput, destinationModule, newRate, outgoingTupleType, initialTupleType);
                }
            }
        }
    }

    protected void calculateEdgeRate() {
        for (AppEdge edge : getApplication().getEdges()) {
            if (edge.getEdgeType() == AppEdge.SENSOR) {
                double currentRate = 1.0;
                String initialTupleType = edge.getTupleType();
                HashMap<String, Double> mapTargetEdge = null;
                if ((mapTargetEdge = mapEdgeRateValues.get(edge.getTupleType())) != null) {
                    Double finalvalue;
                    if ((finalvalue = mapTargetEdge.get(initialTupleType)) != null) {
                        finalvalue += currentRate;
                        mapTargetEdge.put(initialTupleType, finalvalue);
                    } else {
                        mapTargetEdge.put(initialTupleType, currentRate);
                    }
                } else {
                    mapTargetEdge = new HashMap<String, Double>();
                    mapTargetEdge.put(initialTupleType, currentRate);
                    mapEdgeRateValues.put(edge.getTupleType(), mapTargetEdge);
                }
                pathEdgeRate(edge, edge, currentRate);
            }
        }
    }

    protected void calculateModRate() {
        for (AppModule module : getApplication().getModules()) {
            for (AppEdge edge : getApplication().getEdges()) {
                if (edge.getDestination().equals(module.getName()) && edge.getEdgeType() == AppEdge.SENSOR) {
                    double currentRate = 1.0;
                    String initialTupleType = edge.getTupleType();
                    HashMap<String, Double> mapTargetModule = null;
                    if ((mapTargetModule = mapModRateValues.get(module.getName())) != null) {
                        Double finalvalue;
                        if ((finalvalue = mapTargetModule.get(initialTupleType)) != null) {
                            finalvalue += currentRate;
                            mapTargetModule.put(initialTupleType, finalvalue);
                        } else {
                            mapTargetModule.put(initialTupleType, currentRate);
                        }
                    } else {
                        mapTargetModule = new HashMap<String, Double>();
                        mapTargetModule.put(initialTupleType, currentRate);
                        mapModRateValues.put(module.getName(), mapTargetModule);
                    }
                    pathModRate(module, module, currentRate, edge.getTupleType(), initialTupleType);
                }
            }
        }
    }

    public List<Sensor> getSensors() {
        return sensors;
    }

    private void setAssociatedSensors(FogDevice device) {
        for (Sensor sensor : getSensors()) {
            if (sensor.getGatewayDeviceId() == device.getId()) {
                double meanT = sensor.getTransmitDistribution().getMeanInterTransmitTime();
                meanT = 1.0 / meanT;
                String initialTupleType = sensor.getTupleType();
                HashMap<String, Double> mapDeviceModule = null;
                if ((mapDeviceModule = mapDeviceSensorRate.get(device.getName())) != null) {
                    Double finalvalue;
                    if ((finalvalue = mapDeviceModule.get(initialTupleType)) != null) {
                        finalvalue += meanT;
                        mapDeviceModule.put(initialTupleType, finalvalue);
                    } else {
                        mapDeviceModule.put(initialTupleType, meanT);
                    }
                } else {
                    mapDeviceModule = new HashMap<String, Double>();
                    mapDeviceModule.put(initialTupleType, meanT);
                    mapDeviceSensorRate.put(device.getName(), mapDeviceModule);
                }
            }
        }
    }

    protected void sumChildrenRates(FogDevice dev) {
        HashMap<String, Double> mapDev;
        if ((mapDev = mapDeviceSensorRate.get(dev.getName())) == null) {
            mapDev = new HashMap<String, Double>();
            mapDeviceSensorRate.put(dev.getName(), mapDev);
        }
        for (Integer chdDevId : dev.getChildrenIds()) {
            FogDevice chdDev = getFogDeviceById(chdDevId);
            sumChildrenRates(chdDev);
            //No importa comprobar si está o no, si no está es que no habría hecho el recorrido en profundidad, pues al
            //hacer el recorrido lo primero es crearlo si no existe (ver las primeras lineas de este metodo)
            HashMap<String, Double> mapChdSensor = mapDeviceSensorRate.get(chdDev.getName());
            for (Map.Entry<String, Double> sensor : mapChdSensor.entrySet()) {
                String sensorName = sensor.getKey();
                Double sensorValue = sensor.getValue();
                Double storedvalue;
                if ((storedvalue = mapDev.get(sensorName)) != null) {
                    mapDev.put(sensorName, sensorValue + storedvalue);
                    //System.out.println("Le sumo al device "+dev.getName()+" el valor "+sensorValue+" de su hijo "+chdDev.getName()+" con su valor actual "+storedvalue+" para el sensor "+sensorName);
                } else {
                    mapDev.put(sensorName, sensorValue);
                    //System.out.println("Le fijo al device "+dev.getName()+" el valor "+sensorValue+" de su hijo "+chdDev.getName()+" para el sensor "+sensorName);
                }
            }
        }
    }

    protected void calculateDeviceSensorDependencies() {
        int maxDevId = 0;
        List<FogDevice> leafDevices = new ArrayList<FogDevice>();
        FogDevice rootDev = null;
        for (FogDevice dev : getFogDevices()) {
            if (dev.getId() > maxDevId)
                maxDevId = dev.getId();
            if (dev.getChildrenIds().isEmpty()) {
                leafDevices.add(dev);
            }
            if (dev.getLevel() == 0) {
                rootDev = dev;
            }
        }
        for (FogDevice dev : leafDevices) {
            setAssociatedSensors(dev);
        }
        sumChildrenRates(rootDev);

    }


    /**
     * this function will make sure that all tasks are assigned
     *
     * @param list:               positions/velocity matrix
     * @param assignedTasksArray: the tracking array of the 0's and 1's when assiging fog nodes
     * @return positions/velocity matrix
     */
    private ArrayList<int[]> checkResourceAssignmentForNonAssignedTasks(ArrayList<int[]> list, int[] assignedTasksArray) {

        ArrayList<int[]> newArrList = list;

        // check if task is not yet assigned
        for (int i = 0; i < assignedTasksArray.length; i++) {

            if (assignedTasksArray[i] == 0) {

                int x = ran.nextInt(m);

                int[] positions = newArrList.get(x);
                positions[i] = 1;

                newArrList.set(x, positions);
            }
        }
        //newArrList.remove(0);
        return newArrList;
    }

    // This function will re-balance the solution found by PSO for better solutions
    private ArrayList<int[]> ReBalancePSO(ArrayList<int[]> newPositionsMatrix, ArrayList<double[]> runTime) {

        boolean done = false;
        int counter = 0;

        while (!done) {

            double[] sum = new double[m];

            for (int i = 0; i < m; i++) {

                double[] time = runTime.get(i);
                int[] pos = newPositionsMatrix.get(i);

                int n = pos.length;

                for (int j = 0; j < n; j++) {
                    if (pos[j] == 1) {
                        sum[i] = sum[i] + time[j];
                    }
                }
            }

            int heavestFOGLoad = 0;
            int lightestFOGLoad = 0;

            for (int i = 1; i < m; i++) {
                if (sum[heavestFOGLoad] < sum[i]) {
                    heavestFOGLoad = i;
                }

                if (sum[lightestFOGLoad] > sum[i]) {
                    lightestFOGLoad = i;
                }
            }

            int[] HeavestPOS = newPositionsMatrix.get(heavestFOGLoad);

            int[] LightestPOS = newPositionsMatrix.get(lightestFOGLoad);

            for (int i = 0; i < HeavestPOS.length; i++) {
                int cloudletNumberOnHeavest = 0;

                if (HeavestPOS[i] == 1) {
                    cloudletNumberOnHeavest = i;
                }

                double heavestMinusThisCloudlet = sum[heavestFOGLoad] - HeavestPOS[cloudletNumberOnHeavest];
                double LightestPlusThisCloudlet = sum[lightestFOGLoad] + LightestPOS[cloudletNumberOnHeavest];

                if (heavestMinusThisCloudlet < LightestPlusThisCloudlet) {
                    break;
                } else {
                    HeavestPOS[cloudletNumberOnHeavest] = 0;
                    LightestPOS[cloudletNumberOnHeavest] = 1;
                    newPositionsMatrix.set(heavestFOGLoad, HeavestPOS);
                    newPositionsMatrix.set(lightestFOGLoad, LightestPOS);
                }
            }

            //----

            if (counter == 3) {
                done = true;
            }

            counter++;
        }

        return newPositionsMatrix;
    }

    /**
     * will calculate the fitness value of the current particle's solution
     *
     * @param runTime:          the list of execution times of all cloudlets on all VMs
     * @param positionsArrList: the positions matrix found by current particle
     * @return double fitness value
     */
    private double ObjectiveFunction(ArrayList<double[]> runTime, ArrayList<double[]> usageRAM, ArrayList<int[]> positionsArrList) {

        double[] sum = new double[m];

        for (int i = 0; i < m; i++) {

            double[] time = runTime.get(i);
            int[] pos = positionsArrList.get(i);

            int n = pos.length;

            for (int j = 0; j < n; j++) {
                if (pos[j] == 1) {
                    sum[i] = sum[i] + time[j];
                }
            }
        }

        double result = 0;

        // will find the highest execution time among all
        for (int i = 0; i < m; i++) {
            if (result < sum[i]) {
                result = sum[i];
            }
        }
        //System.out.println(result);
        return result;
    }

    /**
     * will calculate the RIW according to
     * (A new particle swarm optimization algorithm with random inertia weight and evolution strategy: paper)
     *
     * @param particleNumber:   The particle's number; one of the possible solutions
     * @param iterationNumber:  The move number when searching the space
     * @param averageFitnesses: The average of all fitness found so far during the first to current iteration number
     * @return double value of the inertia weight
     */
    private double InertiaValue(int particleNumber, int iterationNumber, ArrayList<double[]> averageFitnesses) {

        int k = 5;
        double w = 0.0;

        double w_max = 0.9;
        double w_min = 0.1;

        double t_max = numberOfIterations;
        double t = iterationNumber;

        //if t is multiple of k; use RIW method
        if (t % k == 0 && t != 0) {

            //annealing probability
            double p = 0;

            double currentFitness = averageFitnesses.get(particleNumber)[iterationNumber];
            double previousFitness = averageFitnesses.get(particleNumber)[iterationNumber - k];

            if (previousFitness <= currentFitness) {
                p = 1;
            } else {
                //annealing temperature
                double coolingTemp_Tt = 0.0;

                Particle currParticle = swarm[particleNumber];
                double bestFitness = currParticle.bestFitness;

                double ParticleFitnessAverage = 0;

                int counter = 0;
                for (int i = 0; i < iterationNumber; i++) {
                    if (averageFitnesses.get(particleNumber)[i] > 0) {
                        ParticleFitnessAverage += averageFitnesses.get(particleNumber)[i];
                        counter++;
                    }
                }

                ParticleFitnessAverage = ParticleFitnessAverage / counter;

                coolingTemp_Tt = (ParticleFitnessAverage / bestFitness) - 1;

                p = Math.exp(-(previousFitness - currentFitness) / coolingTemp_Tt);

            }

            int random = ran.nextInt(2);

            //new inertia weight
            if (p >= random) {
                w = 1 + random / 2;
            } else {
                w = 0 + random / 2;
            }
        } else {

            //new inertia weight using LDIW
            double w_fraction = (w_max - w_min) * (t_max - t) / t_max;
            w = w_max - w_fraction;
        }

        return w;
    }

}
