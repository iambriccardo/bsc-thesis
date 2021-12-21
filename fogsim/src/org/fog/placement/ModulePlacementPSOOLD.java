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

/**
 *
 * @author Oscar
 * 
 * 
 * RESTRICCIONES
 * 
 * -No hay tuples periódicas
 * -Solo puede llegar sensores al module Source
 * -Solo cojo los modules SOURCE que al menos le llega un sensor
 * -Solo puede haber un TUPLE que llegue como tipo SENSOR al module Source
 * - La estructura de los modulos ha de ser un arbol con igual profundidad para todos los caminos.
 * 
 * 
 * 
 */
public class ModulePlacementPSOOLD extends ModulePlacement {
	protected ModuleMapping moduleMapping;
    protected List<Sensor> sensors;  // List of sensors
    protected List<Actuator> actuators;  //List of actuators
    protected HashMap<String, HashMap<String, Double>> mapModRateValues = new HashMap<String, HashMap<String,Double>>();  //Calculated value for the request rate that arrives to each module from each single (1.0) request to each incoming Tuple from a sensor
    protected HashMap<String, HashMap<String, Double>> mapEdgeRateValues = new HashMap<String, HashMap<String,Double>>();  //Calculated value for the request rate that arrives to each Tuple from each single (1.0) request to each app-incoming Tuple from a sensor
    protected HashMap<String, HashMap<String, Double>> mapDeviceSensorRate = new HashMap<String, HashMap<String,Double>>();  //Calculated value for the request rate that arrives to each device adding all the sensor rates that are lower to them
    protected Set<FogDevice> gateways = new HashSet<FogDevice>();  //List of the leaf devices where the mobile devices are connecte
    protected Map<FogDevice, Double> currentCpuLoad = new HashMap<FogDevice, Double>();  //Load of the cpu of each device in MIPS
    protected List<Pair<FogDevice, String>> modulesToPlace = new ArrayList<Pair<FogDevice,String>>();  // SAR (Service Allocation Requests) that are stil pending to be analyzed and decide to be placed


	protected Map<FogDevice, List<String>> currentModuleMap = new HashMap<FogDevice, List<String>>();  //Preallocated list of pairs device-module
	protected List<String> moduleOrder = new ArrayList<String>();  //Order of the modules of the app that need to be respected to accomplish the consumption relationships
	protected Map<String, List<String>> mapModuleClosure = new HashMap<String, List<String>>();  //The closure of each element in the app graph

	public int numOfMigrations = 0;
	public int numOfCloudPlacements = 0;
	protected Application application;


	//number of VMs
	int m;
	//number of cloudlets
	int n;

	public int countErrors=0;
	int numberOfParticles = 100;
	int numberOfIterations = 1;

	Particle[] swarm;
	Random ran = new Random();


	public Integer aaaa() {
		return numOfMigrations;
	}

//---------------------------------------------------------------------------------------------------------------------------
//---------------------------------------------------------------------------------------------------------------------------

    public ModulePlacementPSOOLD(List<FogDevice> fogDevices,
                                 List<Sensor> sensors,
                                 List<Actuator> actuators,
                                 Application application,
                                 ModuleMapping moduleMapping,
                                 Integer[] subAppsRate,
                                 String resultsFN) {

    	this.setFogDevices(fogDevices);
        this.setApplication(application);
        this.setModuleToDeviceMap(new HashMap<String, List<Integer>>());
        this.setDeviceToModuleMap(new HashMap<Integer, List<AppModule>>());
        this.sensors = sensors;
        this.moduleMapping = moduleMapping;
        this.application = application;
		for(FogDevice dev : getFogDevices()){
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
		calculate_hop_count();

        //System.out.println("NUMBER of migrations:"+numOfMigrations);
        //System.out.println("NUMBER of cloud placements:"+numOfCloudPlacements);

       try {

		   File archivoCPU = new File("./CPUpop"+resultsFN+".csv");
	       File archivoRAT = new File("./RATpop"+resultsFN+".csv");
	       BufferedWriter bwCPU;
	       BufferedWriter bwRAT;
		   bwCPU = new BufferedWriter(new FileWriter(archivoCPU));
	       bwRAT = new BufferedWriter(new FileWriter(archivoRAT));
	       bwCPU.write("hopcount;cpuusage;cputotal;numservices\n");
	       bwRAT.write("hopcount;requestratio\n");

	       Integer maxLevel=0;
		   for(FogDevice dev : getFogDevices()){
			   maxLevel = Math.max(dev.getLevel(),maxLevel);
		   }

		   for(FogDevice dev : getFogDevices()){
			   Integer hopcountOF = maxLevel-dev.getLevel();
			   Double cpuusageOF = currentCpuLoad.get(dev);
			   Integer cputotalOF = dev.getHost().getTotalMips();
			   Integer numservicesOF = currentModuleMap.get(dev).size();
			   if ( hopcountOF > 0 ) {
				   bwCPU.write(hopcountOF+";"+cpuusageOF+";"+cputotalOF+";"+numservicesOF+"\n");
			   }
		   }

		   for(FogDevice dev : getFogDevices()){
			   List<String> allocatedModules = currentModuleMap.get(dev);
			   for (String moduleAlloc : allocatedModules) {
				   Integer appId = Integer.parseInt(moduleAlloc.substring(moduleAlloc.length()-1));
				   double moduleRateOF = calculateModuleRate(dev, moduleAlloc);

				   Integer hopcountOF = maxLevel-dev.getLevel();
				   Integer requestratioOF = subAppsRate[appId];
				   if ( hopcountOF > 0 ) {
					   bwRAT.write(hopcountOF+";"+requestratioOF+";"+moduleRateOF+"\n");
				   }
			   }
		   }

		   bwCPU.close();
		   bwRAT.close();
       
       } catch (IOException e) {
   			// TODO Auto-generated catch block
   			e.printStackTrace();
   		}
    }

    //Used to check how much
    protected void calculate_hop_count() {
    	double weighted_hop_count = 0.0;
        double weighted_ratio = 0.0;
        double hop_count = 0.0;
        double num_evaluations = 0.0;
    	
		for (Sensor sensor : sensors) {
			for (AppModule mod : getApplication().getModules()) {
				Double ratioMod = mapModRateValues.get(mod.getName()).get(sensor.getTupleType());
				if (ratioMod!=null) {
					Double ratioSen = 1.0/sensor.getTransmitDistribution().getMeanInterTransmitTime();
					Double ratio = ratioMod * ratioSen;
//					System.out.println("tupla:"+sensor.getTupleType());
//					System.out.println("mod:"+mod.getName());
//					System.out.println("ratemod:"+mapModRateValues.get(mod.getName()).get(sensor.getTupleType()));;
//					System.out.println("ratetot:"+ratio);
					FogDevice mobile =getFogDeviceById(sensor.getGatewayDeviceId());
					num_evaluations ++;

					double hc = hopCount(mobile,mod.getName()); //the mobile to which the sensor is connected it is not considered
//					System.out.println("hc:"+hc);

//					System.out.println((double)((double)ratio*(double)hc));
					hop_count += hc;
					weighted_hop_count += (double)((double)ratio*(double)hc);
					weighted_ratio += ratio;
				}
			}
       }
       
//       System.out.println("t"+weighted_hop_count);
//       System.out.println("t"+weighted_ratio);
       weighted_hop_count = (double)((double)weighted_hop_count/ (double)weighted_ratio);
       hop_count = hop_count/num_evaluations;
       System.out.println("Total average weighted hop count: "+weighted_hop_count);
       System.out.println("Total average hop count: "+hop_count);
    }

    protected int hopCount(FogDevice dev, String modName) {
		if (dev.getLevel()==0) {
			return 0;
		}
		if (currentModuleMap.get(dev).contains(modName)) {
			return 0;
		}
		return 1+hopCount(getFogDeviceById(dev.getParentId()),modName);
    }
    
    protected void mapModulesPreviouslyMapped() {
    	for(String deviceName : moduleMapping.getModuleMapping().keySet()){
			for(String moduleName : moduleMapping.getModuleMapping().get(deviceName)){
				int deviceId = CloudSim.getEntityId(deviceName);
				PreAllocate(getFogDeviceById(deviceId),moduleName);
			}
		}
	}
    
    protected void PreAllocate(FogDevice dev, String mod) {
		List<String> currentModules;
		if ( (currentModules = currentModuleMap.get(dev)) != null ) {
			currentModules.add(mod);
		} else {
			currentModules = new ArrayList<String>();
			currentModules.add(mod);
			currentModuleMap.put(dev,currentModules);
		}
    }
    
    protected double calculateResourceUsageEdge(FogDevice dev, AppEdge edge) {
		double totalUsage = 0.0;
		HashMap<String, Double> deviceRequestRate = mapDeviceSensorRate.get(dev.getName());
		HashMap<String, Double> edgeRequestRate = mapEdgeRateValues.get(edge.getTupleType());
		for (Map.Entry<String, Double> edgeMapEntry : edgeRequestRate.entrySet()) {
			Double deviceRate;
			if ( (deviceRate = deviceRequestRate.get(edgeMapEntry.getKey()))!=null ) {
				totalUsage += deviceRate * edgeMapEntry.getValue() * edge.getTupleCpuLength();
				System.out.println("Calculating resource usage for device "+dev.getName()+" (deviceRate="+deviceRate+") and edge "+edge.getTupleType()+" (edgeRequestRate="+edgeMapEntry.getValue()+") with a CPU length of the edge of "+edge.getTupleCpuLength());
			}
		}
		return totalUsage;
	}

    protected double calculateResourceUsage(FogDevice dev, String module) {
		double totalUsage = 0.0;
		System.out.println("***** Calculating resource usage for module "+module);
		for (AppEdge edge : getApplication().getEdges()) {
			if (edge.getDestination().equals(module)) {
				totalUsage += calculateResourceUsageEdge(dev,edge);
			}
		}
		return totalUsage;
	}

    protected double calculateModuleRate(FogDevice dev, String modName) {
		double totalRate = 0.0;
		HashMap<String, Double> deviceRequestRate = mapDeviceSensorRate.get(dev.getName());
		HashMap<String, Double> modRequestRate = mapModRateValues.get(modName);
		for (Map.Entry<String, Double> modMapEntry : modRequestRate.entrySet()) {
			Double deviceRate;
			if ( (deviceRate = deviceRequestRate.get(modMapEntry.getKey()))!=null ) {
				totalRate += deviceRate * modMapEntry.getValue();
			}
		}
		return totalRate;
    }
    
    protected void atToPendingList(Pair<FogDevice,String> pair) {
    		if (!modulesToPlace.contains(pair)) {
    			modulesToPlace.add(pair);  
    		}
    }

	protected void Placement() {
		int numOptExecutions = 0;
		//System.out.println("Test 8.1");
		System.out.println("Modules to place: " + modulesToPlace.size());
		System.out.println("Fog devices number: " + this.getFogDevices().size());

		/*-------------------------------------------------------------------------------------------------------------------*/
		/*-------------------------------------------PSO---------------------------------------------------------------------*/
		/*-------------------------------------------------------------------------------------------------------------------*/
		int m_fog_devices = this.getFogDevices().size();
		int n_modules = modulesToPlace.size();

		n = n_modules;
		m = m_fog_devices;

		System.out.println("m_fog_devices "+ m_fog_devices);
		System.out.println("n_modules "+ n_modules);

		ArrayList<double[]> runTime = new ArrayList<double[]>();

		//will calculate the execution time each module takes if it runs on one of the VMs
		for (int i = 0; i < m_fog_devices ; i++) {

			//FogDevice fogdevice =
			//System.out.println(this.getFogDevices().get(i).getRatePerMips() );
			//System.out.println("Host name: "+this.getFogDevices().get(i).getHost());
			//System.out.println("TotalMips: "+this.getFogDevices().get(i).getHost().getTotalMips());
			//System.out.println(this.getFogDevices().get(i).getName());
			double[] arr = new double[n_modules];

			for	 (int j = 0; j < n_modules; j++) {
				//System.out.println(j);
				Pair<FogDevice,String> module_i = modulesToPlace.get(j);
				//System.out.println(module_i.toString());
				//System.out.println(module_i.getKey());
				//System.out.println(module_i.getFirst());
				//System.out.println(module_i.getSecond());
				//System.out.println(module_i.getValue());
				//System.out.println("Module Name:"+application.getModules().get(j).getName());
				//System.out.println("Module MIPS requirement:"+application.getModules().get(j).getMips());
				//arr[j] =  (double) 200.00;
				arr[j] = (double) this.getFogDevices().get(i).getHost().getTotalMips() / application.getModules().get(j).getMips()*ran.nextInt();
			}

			runTime.add(arr);
		}

		swarm = new Particle[numberOfParticles];

		ArrayList<int[]> bestGlobalPositions = new ArrayList<int[]>();// the best positions found

		double bestGlobalFitness = Double.MAX_VALUE; // smaller values better

		// +++++++++++++++++++++++>

		System.out.println("swarm:"+swarm.length);

		for (int l = 0; l < swarm.length; ++l) // initialize each Particle in the swarm
		{
			//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
			//+( positions )++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
			//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

			ArrayList<int[]> initPositions = new ArrayList<int[]>();
			int[] assignedTasksArray = new int[n_modules];

			for (int i = 0; i < m_fog_devices; i++) {

				int[] randomPositions = new int[n_modules];

				for (int j = 0; j < n_modules; j++) {

					// if not assigned assign it
					if (assignedTasksArray[j] == 0) {
						randomPositions[j] = ran.nextInt(2);

						if (randomPositions[j] == 1) {
							assignedTasksArray[j] = 1;
						}
					}

					else {
						randomPositions[j] = 0;
					}

				}

				initPositions.add(randomPositions);
			}

			// to assign unassigned tasks
			ArrayList<int[]> newPositionsMatrix = checkResourceAssignmentForNonAssignedTasks(initPositions, assignedTasksArray);

			double fitness = ObjectiveFunction(runTime, newPositionsMatrix);

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
					}

					else {
						randomPositions[j] = 0;
					}
				}

				initVelocities.add(randomPositions);
			}

			swarm[l] = new Particle(newPositionsMatrix, fitness, initVelocities, newPositionsMatrix, fitness);

			// does current Particle have global best position/solution?
			if (swarm[l].fitness < bestGlobalFitness)
			{
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
		for(int i = 0 ; i < swarm.length ; i++){
			averageFitnesses.add(new double[numberOfIterations]);
		}

		for(int iter = 0 ; iter < numberOfIterations ; iter++){

			for (int l = 0; l < swarm.length; ++l){

				//calculate InertiaValue
				double w = InertiaValue(l,iter, averageFitnesses);

				ArrayList<double[]> newVelocitiesMatrix = new ArrayList<double[]>();
				ArrayList<int[]> newPositionsMatrix = new ArrayList<int[]>();
				double newFitness;

				Particle currParticle = swarm[l];

				//to keep track of the zeros and ones in the arrayList per task
				int[] assignedTasksArrayInVelocityMatrix = new int[n_modules];

				for(int i = 0; i < currParticle.velocitiesMatrix.size() ; i++){
					double[] vmVelocities = currParticle.velocitiesMatrix.get(i);
					int[] vmBestPositions = currParticle.bestPositionsMatrix.get(i);
					int[] vmPostitons = currParticle.positionsMatrix.get(i);

					int[] vmGlobalbestPositions = bestGlobalPositions.get(i);

					double[] newVelocities = new double[n_modules];

					//length - 1 => v(t+1) = w*v(t) ..
					for(int j = 0 ; j < vmVelocities.length - 1 ; j++){
						r1 = ran.nextInt(2);
						r2 = ran.nextInt(2);

						if (assignedTasksArrayInVelocityMatrix[j] == 0) {

							//velocity vector
							newVelocities[j] =  (w * vmVelocities[j+1] + c1 * r1 * (vmBestPositions[j] - vmPostitons[j]) + c2 * r2 * (vmGlobalbestPositions[j] - vmPostitons[j]));

							if (newVelocities[j] < minV){
								newVelocities[j] = minV;
							}

							else if (newVelocities[j] > maxV){
								newVelocities[j] = maxV;
							}

							if (newVelocities[j] == 1) {
								assignedTasksArrayInVelocityMatrix[j] = 1;
							}
						}

						else {
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

				for(int i = 0; i < currParticle.velocitiesMatrix.size() ; i++){
					double[] vmVelocities = currParticle.velocitiesMatrix.get(i);

					int[] newPosition = new int[n_modules];

					//length - 1 => v(t+1) = w*v(t) ..
					for(int j = 0 ; j < vmVelocities.length - 1 ; j++){
						int random = ran.nextInt(2);

						if (assignedTasksArrayInPositionsMatrix[j] == 0) {

							//to calculate sigmoid function
							double sig = 1/(1+Math.exp(-vmVelocities[j]));

							if(sig > random){
								newPosition[j] = 1;
							}

							else{
								newPosition[j] = 0;
							}

							if (newPosition[j] == 1) {
								assignedTasksArrayInPositionsMatrix[j] = 1;
							}
						}

						else {
							newPosition[j] = 0;
						}
					}

					//add the new velocities into the arrayList
					newPositionsMatrix.add(newPosition);
				}

				//will check for non assigned tasks
				newPositionsMatrix = checkResourceAssignmentForNonAssignedTasks(newPositionsMatrix, assignedTasksArrayInPositionsMatrix);
				newPositionsMatrix = ReBalancePSO(newPositionsMatrix, runTime);
				currParticle.positionsMatrix = newPositionsMatrix;

				//-------> done with new positions

				newFitness = ObjectiveFunction(runTime, newPositionsMatrix);
				currParticle.fitness = newFitness;

				//if the new fitness is better than what we already found
				//-> set the new fitness as the best fitness so far
				if (newFitness < currParticle.bestFitness)
				{
					currParticle.bestPositionsMatrix = newPositionsMatrix;
					currParticle.bestFitness = newFitness;
				}

				//if the new fitness is better than all solutions found by all particles
				//-> set the new fitness as the global fitness
				if (newFitness < bestGlobalFitness)
				{
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
		System.out.println(bestGlobalPositions.toString());
		System.out.println(bestGlobalPositions.toArray());
		System.out.println(bestGlobalPositions.size());

		//return returnVM2CloudLetArray(bestGlobalPositions);




		/*-------------------------------------------------------------------------------------------------------------------*/
		/*-------------------------------------------PSO---------------------------------------------------------------------*/
		/*-------------------------------------------------------------------------------------------------------------------*/

		/*while (modulesToPlace.size()>0) {
			System.out.println("===================================== "+numOptExecutions);
			numOptExecutions ++;
			Pair<FogDevice,String> pair = modulesToPlace.get(0);
			System.out.println( pair.getKey());
			FogDevice dev = pair.getFirst();
			String modName = pair.getSecond();
			int devMips = dev.getHost().getTotalMips();
			Double currentMips = currentCpuLoad.get(dev);
			System.out.println(dev.getRatePerMips());
			System.out.println(dev.getHost());

			System.out.println("Starting with allocation of module "+modName+" in device "+dev.getName());





/*-------------------------------------------------------------------------------------------------------------------*/


/*

			List<String> currentModules = currentModuleMap.get(dev);
			if (currentModules == null) {
				currentModules = new ArrayList<String>();
				currentModuleMap.put(dev, currentModules);
			}

		System.out.println("Test 8.2");

		if (currentModules.contains(modName)) { //The module is already in the device
			//TODO Update the usage ?????
			System.out.println("Module "+modName+" in device "+dev.getName()+" already allocated, so removed form toAllocate list");
			modulesToPlace.remove(0);
		}else {
			if (dev.getLevel()==0) {
				System.out.println("Module "+modName+" in device "+dev.getName()+" allocated because the device is the cloud");

				currentModules.add(modName);//the device is the cloud
				numOfCloudPlacements++;
				modulesToPlace.remove(0);
			}else {
				double requiredResources = calculateResourceUsage(dev,modName);
					System.out.println("Total dev available CPU MIPS "+devMips);
					System.out.println("Already allocated   CPU MIPS "+currentMips);
					System.out.println("Module required     CPU MIPS "+requiredResources);


				if (devMips < requiredResources) {
					System.out.println("Module "+modName+" in device "+dev.getName()+" send to father because total resources not enough");
//				    		Pair<FogDevice,String> pairFather = new Pair<FogDevice,String>(getFogDeviceById(dev.getParentId()),modName);
//				    		modulesToPlace.add(pairFather);
					sendToFather(dev, modName, Double.MAX_VALUE);
//						modulesToPlace.remove(0);

				}else {

					double availableMips = devMips - currentMips;

					if ( availableMips > requiredResources) {
						System.out.println("Module "+modName+" in device "+dev.getName()+" allocated because enough resources");

						PreAllocate(dev,modName);
						currentCpuLoad.put(dev, currentMips + requiredResources);
						System.out.println("Pre-allocated module "+modName+" in device "+dev.getName());
						modulesToPlace.remove(0);
					}else {
						List<String> candidatesToDeallocate = new ArrayList<String>();
						double deallocatedResources = 0.0;
						double moduleRate = calculateModuleRate(dev, modName);
						HashMap<String,Double> allocModRate = calculateModuleRateAllocatedMod(dev);
						boolean enoughWithDeallocation = true;

						while ( enoughWithDeallocation && ( (availableMips + deallocatedResources)< requiredResources ) ){
							List<String> toDealloc = new ArrayList<String>();
							Pair<Double,List<String>> deallocPair = DeallocateLowerRequestedClosure(dev, modName, candidatesToDeallocate, allocModRate);
							double deallocRat = deallocPair.getFirst();
							toDealloc = deallocPair.getSecond();

							if (deallocRat < moduleRate) {
								double deallocRes = calculateClosureRes(dev,toDealloc);
								deallocatedResources += deallocRes;
								for (String s : toDealloc) {
									candidatesToDeallocate.add(s);
								}

							}else {

								enoughWithDeallocation = false;

							}
						}

						if (enoughWithDeallocation) {
							System.out.println("Module "+modName+" in device "+dev.getName()+" allocated because other module closures with smaller rates are deallocated");

							for (String toDeallocMod : candidatesToDeallocate) {  //Every element in the toDeallocatedMod in the list is send to the father and removed from the currentModuleMap. The usage update is donde after the for with the already calculated usages values.
								Pair<FogDevice,String> pairFather = new Pair<FogDevice,String>(getFogDeviceById(dev.getParentId()),toDeallocMod);
								atToPendingList(pairFather);
								currentModuleMap.get(dev).remove(toDeallocMod);
								System.out.println("Removed from pre-allocated list the module "+toDeallocMod+" in device "+dev.getName());
								numOfMigrations++;
							}
							currentMips -= deallocatedResources;  //the deallocated resources are restados.

							PreAllocate(dev,modName);
							currentCpuLoad.put(dev, currentMips + requiredResources);
							System.out.println("Pre-allocated module "+modName+" in device "+dev.getName());
							modulesToPlace.remove(0);

						}else {
							System.out.println("Module "+modName+" in device "+dev.getName()+" send to parent because not enough resources deallocating other closures");

//						    		Pair<FogDevice,String> pairFather = new Pair<FogDevice,String>(getFogDeviceById(dev.getParentId()),modName);
//						    		modulesToPlace.add(pairFather);								
							sendToFather(dev, modName, requiredResources-availableMips);*/
								modulesToPlace.remove(0);
							/*}
						}
					}
				}
			}
		}*/
	}
   

    @Override
    protected void mapModules() {
    	for (FogDevice gw : gateways) {
			for(String modName : moduleOrder) {
				Pair<FogDevice,String> pair = new Pair<FogDevice,String>(gw,modName);
				atToPendingList(pair);
			}
		}
		Placement();
    	for(FogDevice dev : currentModuleMap.keySet()){
			for(String module : currentModuleMap.get(dev)){
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
    			addRecursiveChildren(module.getName(),moduleClosure);
    			Collections.reverse(moduleClosure);
			mapModuleClosure.put(module.getName(), moduleClosure);
    		}
    }

    protected void addRecursiveChildren(String moduleName, List<String> mylist) {
    		for (AppEdge edge : getApplication().getEdges()) {
    			if (edge.getSource().equals(moduleName)) {
    				if (!mylist.contains(moduleName)) {
    					addRecursiveChildren(edge.getDestination(),mylist);
    				}
    			}
    		}
    		if (!mylist.contains(moduleName)) {
    			mylist.add(moduleName);
    		}
    }
    
    protected void calculateModuleOrder() {
    	for (AppEdge edge : getApplication().getEdges()) {
			if (edge.getEdgeType()==AppEdge.SENSOR) {
				addRecursiveChildren(edge.getDestination(),moduleOrder);
			}
		}
	}
    
    protected void pathEdgeRate(AppEdge pathEdgeInput, AppEdge currentEdge, double currentRate) {
		AppModule currentModule = getApplication().getModuleByName(currentEdge.getDestination());
		for (AppEdge edge : getApplication().getEdges()) {
			if (edge.getSource().equals(currentModule.getName()) && edge.getEdgeType()!=AppEdge.ACTUATOR ){
				SelectivityModel s = currentModule.getSelectivityMap().get(new Pair<String, String>(currentEdge.getTupleType(), edge.getTupleType()));
				if (s!=null) {
					double newRate = currentRate * s.getMeanRate();
					HashMap<String, Double> mapTargetEdge = null;
					if ( ( mapTargetEdge = mapEdgeRateValues.get(edge.getTupleType())) != null ) {
						Double finalvalue;
						if ( (finalvalue = mapTargetEdge.get(pathEdgeInput.getTupleType())) != null ){
							finalvalue += newRate;
							mapTargetEdge.put(pathEdgeInput.getTupleType(), finalvalue);
						} else {
							mapTargetEdge.put(pathEdgeInput.getTupleType(), newRate);
						}
					}else {

						mapTargetEdge = new HashMap<String, Double>();
						mapTargetEdge.put(pathEdgeInput.getTupleType(), newRate);
						mapEdgeRateValues.put(edge.getTupleType(), mapTargetEdge);
					}
					pathEdgeRate(pathEdgeInput,edge,newRate);
				}
			}
		}
    }

    protected void pathModRate(AppModule pathModuleInput, AppModule currentModule, double currentRate, String incomingTupleType, String initialTupleType) {
		for (AppEdge edge : getApplication().getEdges()) {
			if (edge.getSource().equals(currentModule.getName()) && edge.getEdgeType()!=AppEdge.ACTUATOR ){

//				System.out.println("Current:"+currentModule.getName());
//				System.out.print(edge.getTupleType()+"::::");
//				System.out.print(edge.getSource());
//				System.out.print("====>");
//				System.out.println(edge.getDestination());
				AppModule destinationModule = getApplication().getModuleByName(edge.getDestination());
				String outgoingTupleType = edge.getTupleType();
				SelectivityModel s = currentModule.getSelectivityMap().get(new Pair<String, String>(incomingTupleType, outgoingTupleType));
				if (s!=null) {
//					System.out.print(pathModuleInput.getName());
//					System.out.print("====>");
//					System.out.println(destinationModule.getName());

					double newRate = currentRate * s.getMeanRate();
					HashMap<String, Double> mapTargetModule = null;
					if ( ( mapTargetModule = mapModRateValues.get(destinationModule.getName())) != null ) {
						Double finalvalue;
						if ( (finalvalue = mapTargetModule.get(initialTupleType)) != null ){
							finalvalue += newRate;
							mapTargetModule.put(initialTupleType, finalvalue);
						} else {
							mapTargetModule.put(initialTupleType, newRate);
						}
					}else {
						mapTargetModule = new HashMap<String, Double>();
						mapTargetModule.put(initialTupleType, newRate);
						mapModRateValues.put(destinationModule.getName(), mapTargetModule);
					}
					pathModRate(pathModuleInput,destinationModule,newRate,outgoingTupleType,initialTupleType);
				}
			}
		}
	}
    
    protected void calculateEdgeRate() {
		for (AppEdge edge : getApplication().getEdges()) {
			if (edge.getEdgeType()==AppEdge.SENSOR ){
				double currentRate = 1.0;
				String initialTupleType = edge.getTupleType();
				HashMap<String, Double> mapTargetEdge = null;
				if ( ( mapTargetEdge = mapEdgeRateValues.get(edge.getTupleType())) != null ) {
					Double finalvalue;
					if ( (finalvalue = mapTargetEdge.get(initialTupleType)) != null ){
						finalvalue += currentRate;
						mapTargetEdge.put(initialTupleType, finalvalue);
					} else {
						mapTargetEdge.put(initialTupleType, currentRate);
					}
				}else {
					mapTargetEdge = new HashMap<String, Double>();
					mapTargetEdge.put(initialTupleType, currentRate);
					mapEdgeRateValues.put(edge.getTupleType(), mapTargetEdge);
				}
				pathEdgeRate(edge,edge,currentRate);
			}
		}
	}

    protected void calculateModRate() {
        for (AppModule module : getApplication().getModules()) {
			for (AppEdge edge : getApplication().getEdges()) {
				if (edge.getDestination().equals(module.getName()) && edge.getEdgeType()==AppEdge.SENSOR ){
					double currentRate = 1.0;
					String initialTupleType = edge.getTupleType();
					HashMap<String, Double> mapTargetModule = null;
					if ( ( mapTargetModule = mapModRateValues.get(module.getName())) != null ) {
						Double finalvalue;
						if ( (finalvalue = mapTargetModule.get(initialTupleType)) != null ){
							finalvalue += currentRate;
							mapTargetModule.put(initialTupleType, finalvalue);
						} else {
							mapTargetModule.put(initialTupleType, currentRate);
						}
					}else {
						mapTargetModule = new HashMap<String, Double>();
						mapTargetModule.put(initialTupleType, currentRate);
						mapModRateValues.put(module.getName(), mapTargetModule);
					}
					pathModRate(module,module,currentRate,edge.getTupleType(),initialTupleType);
				}
			}
        }
    }
    
	public List<Sensor> getSensors() {
		return sensors;
	}
	
    private void setAssociatedSensors(FogDevice device) {
		for(Sensor sensor : getSensors()){
			if(sensor.getGatewayDeviceId()==device.getId()){
				double meanT = sensor.getTransmitDistribution().getMeanInterTransmitTime();
				meanT = 1.0 / meanT;
				String initialTupleType = sensor.getTupleType();
				HashMap<String, Double> mapDeviceModule = null;
				if ( ( mapDeviceModule = mapDeviceSensorRate.get(device.getName())) != null ) {
					Double finalvalue;
					if ( (finalvalue = mapDeviceModule.get(initialTupleType)) != null ){
						finalvalue += meanT;
						mapDeviceModule.put(initialTupleType, finalvalue);
					} else {
						mapDeviceModule.put(initialTupleType, meanT);
					}
				}else {
					mapDeviceModule = new HashMap<String, Double>();
					mapDeviceModule.put(initialTupleType, meanT);
					mapDeviceSensorRate.put(device.getName(), mapDeviceModule);
				}
			}
		}
	}
    
    protected void sumChildrenRates(FogDevice dev) {
		HashMap<String,Double> mapDev;
		if ( (mapDev = mapDeviceSensorRate.get(dev.getName()))== null ) {
			mapDev = new HashMap<String,Double>();
			mapDeviceSensorRate.put(dev.getName(),mapDev);
		}
		for (Integer chdDevId : dev.getChildrenIds()) {
			FogDevice chdDev = getFogDeviceById(chdDevId);
			sumChildrenRates(chdDev);
			//No importa comprobar si está o no, si no está es que no habría hecho el recorrido en profundidad, pues al
			//hacer el recorrido lo primero es crearlo si no existe (ver las primeras lineas de este metodo)
			HashMap<String,Double> mapChdSensor = mapDeviceSensorRate.get(chdDev.getName());
            for (Map.Entry<String, Double> sensor : mapChdSensor.entrySet()) {
				String sensorName = sensor.getKey();
				Double sensorValue = sensor.getValue();
				Double storedvalue;
				if ( (storedvalue = mapDev.get(sensorName)) != null) {
					mapDev.put(sensorName, sensorValue + storedvalue);
					//System.out.println("Le sumo al device "+dev.getName()+" el valor "+sensorValue+" de su hijo "+chdDev.getName()+" con su valor actual "+storedvalue+" para el sensor "+sensorName);
				}else {
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
		for(FogDevice dev : getFogDevices()){
			if (dev.getId()>maxDevId)
				maxDevId = dev.getId();
			if (dev.getChildrenIds().isEmpty()) {
				leafDevices.add(dev);
			}
			if (dev.getLevel()==0) {
				rootDev = dev;
			}
		}
		for (FogDevice dev : leafDevices) {
			setAssociatedSensors(dev);
		}
		sumChildrenRates(rootDev);

	}


	/**
	 * this function will make sure that all tasks are assigned to one of the VMs
	 *
	 * @param list: positions/velocity matrix
	 * @param assignedTasksArray: the tracking array of the 0's and 1's when assiging VMs to cloudlets
	 *
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

		return newArrList;
	}

	// This function will re-balance the solution found by PSO for better solutions
	private ArrayList<int[]> ReBalancePSO(ArrayList<int[]> newPositionsMatrix, ArrayList<double[]> runTime) {

		boolean done = false;
		int counter = 0;

		while(!done){

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

			int heavestVMLoad = 0;
			int lightestVMLoad = 0;

			for(int i = 1 ; i < m; i++){
				if(sum[heavestVMLoad] < sum[i]){
					heavestVMLoad = i;
				}

				if(sum[lightestVMLoad] > sum[i]){
					lightestVMLoad = i;
				}
			}

			int[] HeavestPOS = newPositionsMatrix.get(heavestVMLoad);

			int[] LightestPOS = newPositionsMatrix.get(lightestVMLoad);

			for(int i = 0 ; i < HeavestPOS.length ; i++){
				int cloudletNumberOnHeavest = 0;

				if(HeavestPOS[i] == 1){
					cloudletNumberOnHeavest = i;
				}

				double heavestMinusThisCloudlet = sum[heavestVMLoad] - HeavestPOS[cloudletNumberOnHeavest];
				double LightestPlusThisCloudlet = sum[lightestVMLoad] + LightestPOS[cloudletNumberOnHeavest];

				if(heavestMinusThisCloudlet < LightestPlusThisCloudlet){
					break;
				}

				else{
					HeavestPOS[cloudletNumberOnHeavest] = 0;
					LightestPOS[cloudletNumberOnHeavest] = 1;
					newPositionsMatrix.set(heavestVMLoad, HeavestPOS);
					newPositionsMatrix.set(lightestVMLoad, LightestPOS);
				}
			}

			//----

			if(counter == 3){
				done = true;
			}

			counter++;
		}

		return newPositionsMatrix;
	}

	/**
	 * will return an integer array of the vm to cloudlet mapping
	 *
	 * @param bestGlobalPositions: best found positions array based on the best fitness found/ minimum makespan
	 *
	 * @return array of integers
	 */
	private int[] returnVM2CloudLetArray(ArrayList<int[]> bestGlobalPositions){

		int cloudLetNumbers = n;

		int[] cloudLetPositions = new int[cloudLetNumbers];

		for(int i = 0 ; i < m ; i++){

			int[] vm = bestGlobalPositions.get(i);

			for(int j = 0 ; j < n ; j++){

				if(vm[j] == 1){
					cloudLetPositions[j] = i;
				}
			}
		}

		return cloudLetPositions;
	}


	/**
	 * will calculate the fitness value of the current particle's solution
	 *
	 * @param runTime: the list of execution times of all cloudlets on all VMs
	 * @param positionsArrList: the positions matrix found by current particle
	 *
	 * @return double fitness value
	 */
	private double ObjectiveFunction(ArrayList<double[]> runTime, ArrayList<int[]> positionsArrList) {

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

		return result;
	}

	/**
	 * will calculate the RIW according to
	 * (A new particle swarm optimization algorithm with random inertia weight and evolution strategy: paper)
	 *
	 * @param particleNumber: The particle's number; one of the possible solutions
	 * @param iterationNumber: The move number when searching the space
	 * @param averageFitnesses: The average of all fitness found so far during the first to current iteration number
	 *
	 * @return double value of the inertia weight
	 */
	private double InertiaValue(int particleNumber, int iterationNumber, ArrayList<double[]> averageFitnesses){

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

			if(previousFitness <= currentFitness){
				p = 1;
			}

			else{
				//annealing temperature
				double coolingTemp_Tt = 0.0;

				Particle currParticle = swarm[particleNumber];
				double bestFitness = currParticle.bestFitness;

				double ParticleFitnessAverage = 0;

				int counter = 0;
				for(int i = 0 ; i < iterationNumber ; i++){
					if(averageFitnesses.get(particleNumber)[i] > 0){
						ParticleFitnessAverage += averageFitnesses.get(particleNumber)[i];
						counter++;
					}
				}

				ParticleFitnessAverage = ParticleFitnessAverage/counter;

				coolingTemp_Tt = (ParticleFitnessAverage / bestFitness) - 1;

				p = Math.exp(-(previousFitness - currentFitness)/coolingTemp_Tt);

			}

			int random = ran.nextInt(2);

			//new inertia weight
			if(p >= random){
				w = 1 + random/2;
			}

			else{
				w = 0 + random/2;
			}
		}

		else{

			//new inertia weight using LDIW
			double w_fraction = ( w_max - w_min ) * ( t_max - t ) / t_max;
			w = w_max - w_fraction;
		}

		return w;
	}

}

