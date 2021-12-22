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

package org.fog.test.perfeval;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.*;
import org.fog.placement.*;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.Config;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

/**
 * Simulation setup for case study 1 - EEG Beam Tractor Game
 * @author Harshit Gupta
 *
 */
public class NAppSockPSO {
	static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
	static List<FogDevice> users = new ArrayList<FogDevice>();
	static List<Sensor> sensors = new ArrayList<Sensor>();
	static List<Actuator> actuators = new ArrayList<Actuator>();
	static List<Integer> configNumOfNetChildren = new ArrayList<Integer>();
	static List<Integer> configNumOfUsersPerRouter = new ArrayList<Integer>();
	static List<Integer> configNumOfNetwroksLevels = new ArrayList<Integer>();
	static List<Integer> configNumOfRepeatedSubApps = new ArrayList<Integer>();
	static List<String> configPlacementPolicy = new ArrayList<String>();
	static int numOfApps =5;
	static Integer numOfNetChildren = 1;
	static int numOfUsersPerRouter = 1;
	static int numOfNetworkLevels = 4;
	static int numOfRepeatedSubApps = 1;
	static int finishTime = 3500;

//  static String placementPolicy = "ModulePlacementEdgewards";
//  static String placementPolicy = "ModulePlacementPopularity";
	static String placementPolicy = "ModulePlacementPSO";

	private static boolean CLOUD = false;
	static Integer[] subAppsRate={30,10,25,35,20,30,10,25,35,20};
//	static Integer[] subAppsRate={30,10,25,30,20,30,10,25,35,20};
//	static Integer[] subAppsRate={300,100,250,300,200,300,100,250,350,200};

	static double EEG_TRANSMISSION_TIME = 10000;
	

	public static void main(String[] args) {
		Integer countErrors=0;
		Log.printLine("Starting Sock Shop...");

        for(int counter = 0; counter < args.length; counter++){
			if (args[counter].startsWith("p=")) {
				placementPolicy = args[counter].substring(2);
			}
			if (args[counter].startsWith("a=")) {
				numOfRepeatedSubApps = Integer.parseInt(args[counter].substring(2));
			}
			if (args[counter].startsWith("l=")) {
				numOfNetworkLevels = Integer.parseInt(args[counter].substring(2));
			}
			if (args[counter].startsWith("u=")) {
				numOfUsersPerRouter = Integer.parseInt(args[counter].substring(2));
			}
			if (args[counter].startsWith("c=")) {
				numOfNetChildren = Integer.parseInt(args[counter].substring(2));
			}
			if (args[counter].startsWith("f=")) {
				finishTime = Integer.parseInt(args[counter].substring(2));
			}
        }
        
        String FileNameResults = "a"+numOfRepeatedSubApps+"l"+numOfNetworkLevels+"u"+numOfUsersPerRouter+"c"+numOfNetChildren;
		
        Config.MAX_SIMULATION_TIME= finishTime;
							
		try {
			Log.disable();
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; // mean trace events

			CloudSim.init(num_user, calendar, trace_flag);

			String[] appId = new String[numOfApps];
			FogBroker[] broker = new FogBroker[numOfApps];
			Application[] application = new Application[numOfApps];
			ModuleMapping[] moduleMapping = new ModuleMapping[numOfApps];

			createFogDevices();

			int currentApp=0;

			for (currentApp=0;currentApp<numOfApps;currentApp++) {
				appId[currentApp] = "/_"+currentApp;
			}

			for (currentApp=0;currentApp<numOfApps;currentApp++) {
				broker[currentApp] = new FogBroker("broker_"+currentApp);
			}

			for (currentApp=0;currentApp<numOfApps;currentApp++) {
				application[currentApp] = createApplication(appId[currentApp], broker[currentApp].getId());
			}

			for (currentApp=0;currentApp<numOfApps;currentApp++) {
				application[currentApp].setUserId(broker[currentApp].getId());
			}

			for (currentApp=0;currentApp<numOfApps;currentApp++) {
				createEdgeDevices(broker[currentApp].getId(), appId[currentApp]);
			}

			for (currentApp=0;currentApp<numOfApps;currentApp++) {
				moduleMapping[currentApp] = ModuleMapping.createModuleMapping();
			}
			FogDevice router = createFogDevice("t-0-0", 100000, 6000, 20000, 20000, 1, 0.0, 107.339, 83.4333, 2);
			fogDevices.add(router);


			Controller controller = new Controller("master-controller", fogDevices, sensors,
					actuators);

			long start_time = System.nanoTime();
			System.out.println("-----------------------------------------------------------------------------------------------------------------------"+System.nanoTime());

			for (currentApp=0;currentApp<numOfApps;currentApp++) {

				ModulePlacement modulePlacement = null;

				if (placementPolicy.equals("ModulePlacementPopularity")) {
					moduleMapping[currentApp] = ModuleMapping.createModuleMapping();
					modulePlacement = new ModulePlacementPopularity(fogDevices, sensors, actuators, application[currentApp], moduleMapping[currentApp],subAppsRate,FileNameResults);
				}

				if (placementPolicy.equals("ModulePlacementEdgewards")) {
					moduleMapping[currentApp] = ModuleMapping.createModuleMapping();
					modulePlacement = new ModulePlacementEdgewards(fogDevices, sensors, actuators, application[currentApp], moduleMapping[currentApp],subAppsRate,FileNameResults);
				}

				if (placementPolicy.equals("ModulePlacementPSO")) {
					//for (int i = 0; i < 100; i++) {
						moduleMapping[currentApp] = ModuleMapping.createModuleMapping();
						modulePlacement = new ModulePlacementPSO(fogDevices, sensors, actuators, application[currentApp], moduleMapping[currentApp],subAppsRate,FileNameResults,countErrors);
					//}
				}

				//this.timeUsed = difference;
				//System.exit(0);
				controller.submitApplication(application[currentApp],modulePlacement);
			}

			long end_time = System.nanoTime();
			double difference = (end_time - start_time) / 1e6;
			System.out.println("Finaliza---------------------------------------------------------------------------------------------------------------------"+difference);

			TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

			//CloudSim.terminateSimulation(finishTime);  //termina la simulación tras 10 ms

			System.out.println("*****SIMULATION FOR*******");
			System.out.println("numOfNetChildren:"+numOfNetChildren);
			System.out.println("numOfUsersPerRouter:"+numOfUsersPerRouter);
			System.out.println("numOfNetworkLevels:"+numOfNetworkLevels);
			System.out.println("numOfRepeatedSubApps:"+numOfRepeatedSubApps);
			System.out.println("placementPolicy:"+placementPolicy);
			System.out.println("finishTime:"+finishTime);
			System.out.println("*******************");

			CloudSim.startSimulation();
			CloudSim.stopSimulation();

			//controller.printresults();
			/*Publicontrollerc methor in class controller that executes
			 *		printTimeDetails()
			 * printPowerDetails()
			 * printCostDetails()
			 * printNetworkUsageDetails()
			 */
			System.out.println("*****END OF SIMULATION*******");
			System.out.println("numOfNetChildren:"+numOfNetChildren);
			System.out.println("numOfUsersPerRouter:"+numOfUsersPerRouter);
			System.out.println("numOfNetworkLevels:"+numOfNetworkLevels);
			System.out.println("numOfRepeatedSubApps:"+numOfRepeatedSubApps);
			System.out.println("placementPolicy:"+placementPolicy);
			System.out.println("finishTime:"+finishTime);
			System.out.println("*******************");

			Log.printLine("VRGame finished!");

		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("HA OCURRIDO UN EXCEPTION");
			System.out.println("*****END OF SIMULATION*******");
			System.out.println("numOfNetChildren:"+numOfNetChildren);
			System.out.println("numOfUsersPerRouter:"+numOfUsersPerRouter);
			System.out.println("numOfNetworkLevels:"+numOfNetworkLevels);
			System.out.println("numOfRepeatedSubApps:"+numOfRepeatedSubApps);
			System.out.println("placementPolicy:"+placementPolicy);
			System.out.println("finishTime:"+finishTime);
			System.out.println("*******************");
			Log.printLine("Unwanted errors happen");
		}

	}

	private static void createEdgeDevices(int userId, String appId) {
		for(FogDevice user : users){
			String id = user.getName();
			
			for (int i=0; i<numOfRepeatedSubApps; i++) {
				String currentAppId = appId +"/"+i;
				Sensor sensor = new Sensor("s-"+currentAppId+"-"+id, "REQUEST"+currentAppId, userId, appId, new DeterministicDistribution(subAppsRate[i])); // inter-transmission time of camera (sensor) follows a deterministic distribution
				sensors.add(sensor);
				sensor.setGatewayDeviceId(user.getId());
				sensor.setLatency(1.0);
			}
		}
	}

	/**
	 * Creates the fog devices in the physical topology of the simulation.
	 * @param userId
	 * @param appId
	 */
	private static void createFogDevices() {
		FogDevice cloud = createFogDevice("cloud", 4480000, 4000000, 10000, 10000,  0, 0.01, 16*103, 16*83.25,100000);
		cloud.setParentId(-1);
		fogDevices.add(cloud);
		FogDevice proxy = createFogDevice("proxy-server", 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333,2);
		//proxy.setParentId(-1);
		proxy.setParentId(cloud.getId());
		//proxy.setUplinkLatency(100); // latency of connection between proxy server and cloud is 100 ms
		fogDevices.add(proxy);
		for(int i=0;i<numOfNetChildren;i++){
			addArea(i+"", proxy.getId(),1);
		}
	}

	private static FogDevice addArea(String id, int parentId,int currentNetLevel){
		FogDevice router = createFogDevice("d-"+id, 2800, 4000, 20000, 20000, 1+currentNetLevel, 0.0, 107.339, 83.4333, 2);
		fogDevices.add(router);
		router.setParentId(parentId);
		//router.setUplinkLatency(2); // latency of connection between router and proxy server is 2 ms
		if (currentNetLevel == numOfNetworkLevels) {
			for(int i=0;i<numOfUsersPerRouter;i++){
				String mobileId = id+"-"+i;
				FogDevice user = addUser(mobileId,router.getId(),2+currentNetLevel); // adding a smart camera to the physical topology. Smart cameras have been modeled as fog devices as well.
				//user.setUplinkLatency(2); // latency of connection between camera and router is 2 ms
				fogDevices.add(user);
			}
		}else {
			for(int i=0;i<numOfNetChildren;i++){
				addArea(id+"-"+i+"", router.getId(),currentNetLevel+1);
			}
		}
		return router;
	}
	
	private static FogDevice addUser(String id, int parentId, int netLevel){
		FogDevice user = createFogDevice("m-"+id, 1, 1, 10000, 10000, netLevel, 0, 87.53, 82.44, 0);
		user.setParentId(parentId);
		users.add(user);
		return user;
	}

	/**
	 * Creates a vanilla fog device
	 * @param nodeName name of the device to be used in simulation
	 * @param mips MIPS
	 * @param ram RAM
	 * @param upBw uplink bandwidth
	 * @param downBw downlink bandwidth
	 * @param level hierarchy level of the device
	 * @param ratePerMips cost rate per MIPS used
	 * @param busyPower
	 * @param idlePower
	 * @return
	 */
	private static FogDevice createFogDevice(String nodeName, long mips,
			int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower, double upLinkLatency) {
		List<Pe> peList = new ArrayList<Pe>();
		// 3. Create PEs and add these into a list.
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating
		int hostId = FogUtils.generateEntityId();
		long storage = 1000000; // host storage
		long bw = upBw+downBw;
		PowerHost host = new PowerHost(
				hostId,
				new RamProvisionerSimple(ram),
				new BwProvisionerOverbooking(bw),
				storage,
				peList,
				new StreamOperatorScheduler(peList),
				new FogLinearPowerModel(busyPower, idlePower)
			);
		List<Host> hostList = new ArrayList<Host>();
		hostList.add(host);
		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		double time_zone = 10.0; // time zone this resource located
		double cost = 300.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this resource
		double costPerBw = 0.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN devices by now
		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
				arch, os, vmm, host, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);
		FogDevice fogdevice = null;
		try {
			fogdevice = new FogDevice(nodeName, characteristics, 
					new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, upLinkLatency, ratePerMips);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		fogdevice.setLevel(level);
		return fogdevice;
	}

	
	@SuppressWarnings({"serial" })
	private static Application createApplication(String appId, int userId){
		List<AppLoop> loops = new ArrayList<AppLoop>();
		Application application = Application.createApplication(appId, userId);
		for (int i=0; i<numOfRepeatedSubApps; i++) {
			String currentAppId = appId +"/"+i;
			/*
			 * Adding modules (vertices) to the application model (directed graph)
			 */
			application.addAppModule("front_end"+currentAppId, 1);
			application.addAppModule("edge_router"+currentAppId, 1);
			application.addAppModule("login"+currentAppId, 1);
			application.addAppModule("accounts"+currentAppId, 1);
			application.addAppModule("catalogue"+currentAppId, 1);
			application.addAppModule("orders"+currentAppId, 1);
			application.addAppModule("cart"+currentAppId, 1);
			application.addAppModule("payment"+currentAppId, 1);
			application.addAppModule("shipping"+currentAppId, 1);
	//		application.addAppModule("kk"+currentAppId, 10);
			
			
			/*
			 * Connecting the application modules (vertices) in the application model (directed graph) with edges
			 */
			application.addAppEdge("edge_router"+currentAppId, "front_end"+currentAppId, 1000, 10.0, "BROWSE"+currentAppId, Tuple.UP, AppEdge.MODULE); // adding edge from Motion Detector to Object Detector module carrying tuples of type MOTION_VIDEO_STREAM
			application.addAppEdge("REQUEST"+currentAppId, "edge_router"+currentAppId, 1000, 10.0, "REQUEST"+currentAppId, Tuple.UP, AppEdge.SENSOR); // adding edge from CAMERA (sensor) to Motion Detector module carrying tuples of type CAMERA
			application.addAppEdge("front_end"+currentAppId, "login"+currentAppId, 1000, 10.0, "IDENTIFY"+currentAppId, Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to Object Tracker module carrying tuples of type OBJECT_LOCATION
			application.addAppEdge("login"+currentAppId, "accounts"+currentAppId, 1000, 10.0, "LOG_U"+currentAppId, Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to Object Tracker module carrying tuples of type OBJECT_LOCATION
			application.addAppEdge("front_end"+currentAppId, "accounts"+currentAppId, 1000, 10.0, "LOG_B"+currentAppId, Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to User Interface module carrying tuples of type DETECTED_OBJECT
			application.addAppEdge("front_end"+currentAppId, "catalogue"+currentAppId, 1000, 10.0, "SELECT"+currentAppId, Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to Object Tracker module carrying tuples of type OBJECT_LOCATION
			application.addAppEdge("front_end"+currentAppId, "orders"+currentAppId, 1000, 10.0, "BUY"+currentAppId, Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to Object Tracker module carrying tuples of type OBJECT_LOCATION
			application.addAppEdge("front_end"+currentAppId, "cart"+currentAppId, 1000, 10.0, "SEE"+currentAppId, Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to Object Tracker module carrying tuples of type OBJECT_LOCATION
			application.addAppEdge("orders"+currentAppId, "cart"+currentAppId, 1000, 10.0, "ADD"+currentAppId, Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to Object Tracker module carrying tuples of type OBJECT_LOCATION
			application.addAppEdge("orders"+currentAppId, "payment"+currentAppId, 1000, 10.0, "PAY"+currentAppId, Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to Object Tracker module carrying tuples of type OBJECT_LOCATION
			application.addAppEdge("orders"+currentAppId, "shipping"+currentAppId, 1000, 10.0, "SEND"+currentAppId, Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to Object Tracker module carrying tuples of type OBJECT_LOCATION
			application.addAppEdge("orders"+currentAppId, "accounts"+currentAppId, 1000, 10.0, "LOG_O"+currentAppId, Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to Object Tracker module carrying tuples of type OBJECT_LOCATION
	//		application.addAppEdge("cart"+currentAppId, "kk"+currentAppId, 1000, 100, "KK1"+currentAppId, Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to Object Tracker module carrying tuples of type OBJECT_LOCATION
	//		application.addAppEdge("accounts"+currentAppId, "kk"+currentAppId, 1000, 100, "KK2"+currentAppId, Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to Object Tracker module carrying tuples of type OBJECT_LOCATION
	
			
			
			/*
			 * Defining the input-output relationships (represented by selectivity) of the application modules. 
			 */
			application.addTupleMapping("edge_router"+currentAppId, "REQUEST"+currentAppId, "BROWSE"+currentAppId, new FractionalSelectivity(1.0)); // 1.0 tuples of type MOTION_VIDEO_STREAM are emitted by Motion Detector module per incoming tuple of type CAMERA
			application.addTupleMapping("front_end"+currentAppId, "BROWSE"+currentAppId, "LOG_B"+currentAppId, new FractionalSelectivity(1.0)); // 1.0 tuples of type OBJECT_LOCATION are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM
			application.addTupleMapping("front_end"+currentAppId, "BROWSE"+currentAppId, "IDENTIFY"+currentAppId, new FractionalSelectivity(1.0)); // 0.05 tuples of type MOTION_VIDEO_STREAM are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM
			application.addTupleMapping("login"+currentAppId, "IDENTIFY"+currentAppId, "LOG_U"+currentAppId, new FractionalSelectivity(1.0)); // 0.05 tuples of type MOTION_VIDEO_STREAM are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM
			application.addTupleMapping("front_end"+currentAppId, "BROWSE"+currentAppId, "SELECT"+currentAppId, new FractionalSelectivity(1.0)); // 0.05 tuples of type MOTION_VIDEO_STREAM are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM
			application.addTupleMapping("front_end"+currentAppId, "BROWSE"+currentAppId, "BUY"+currentAppId, new FractionalSelectivity(1.0)); // 0.05 tuples of type MOTION_VIDEO_STREAM are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM
			application.addTupleMapping("front_end"+currentAppId, "BROWSE"+currentAppId, "SEE"+currentAppId, new FractionalSelectivity(1.0)); // 0.05 tuples of type MOTION_VIDEO_STREAM are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM
			application.addTupleMapping("orders"+currentAppId, "BUY"+currentAppId, "ADD"+currentAppId, new FractionalSelectivity(1.0)); // 0.05 tuples of type MOTION_VIDEO_STREAM are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM
			application.addTupleMapping("orders"+currentAppId, "BUY"+currentAppId, "PAY"+currentAppId, new FractionalSelectivity(1.0)); // 0.05 tuples of type MOTION_VIDEO_STREAM are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM
			application.addTupleMapping("orders"+currentAppId, "BUY"+currentAppId, "SEND"+currentAppId, new FractionalSelectivity(1.0)); // 0.05 tuples of type MOTION_VIDEO_STREAM are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM
			application.addTupleMapping("orders"+currentAppId, "BUY"+currentAppId, "LOG_O"+currentAppId, new FractionalSelectivity(1.0)); // 0.05 tuples of type MOTION_VIDEO_STREAM are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM
//			application.addTupleMapping("cart"+currentAppId, "ADD"+currentAppId, "KK1"+currentAppId, new FractionalSelectivity(1.0)); // 0.05 tuples of type MOTION_VIDEO_STREAM are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM
//			application.addTupleMapping("cart"+currentAppId, "SEE"+currentAppId, "KK1"+currentAppId, new FractionalSelectivity(1.0)); // 0.05 tuples of type MOTION_VIDEO_STREAM are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM
//	//		application.addTupleMapping("accounts"+currentAppId, "LOG_O"+currentAppId, "KK2"+currentAppId, new FractionalSelectivity(1.0)); // 0.05 tuples of type MOTION_VIDEO_STREAM are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM
	//		application.addTupleMapping("accounts"+currentAppId, "LOG_U"+currentAppId, "KK2"+currentAppId, new FractionalSelectivity(1.0)); // 0.05 tuples of type MOTION_VIDEO_STREAM are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM
	//		application.addTupleMapping("accounts"+currentAppId, "LOG_B"+currentAppId, "KK2"+currentAppId, new FractionalSelectivity(1.0)); // 0.05 tuples of type MOTION_VIDEO_STREAM are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM

			List<String> modules;
			modules = new ArrayList<String>();
			modules.add("edge_router"+currentAppId);
			modules.add("front_end"+currentAppId);
			modules.add("orders"+currentAppId);
			modules.add("accounts"+currentAppId);
			AppLoop loop1 = new AppLoop(modules);
			loops.add(loop1);
		}
		application.setLoops(loops);
		return application;
	}

}