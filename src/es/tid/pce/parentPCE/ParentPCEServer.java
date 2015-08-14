package es.tid.pce.parentPCE;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import es.tid.bgp.bgp4Peer.peer.BGPPeer;
import es.tid.pce.computingEngine.ReportDispatcher;
import es.tid.pce.computingEngine.RequestDispatcher;
import es.tid.pce.computingEngine.algorithms.ComputingAlgorithmManager;
import es.tid.pce.computingEngine.algorithms.LocalChildRequestManager;
import es.tid.pce.computingEngine.algorithms.ParentPCEComputingAlgorithmManager;
import es.tid.pce.parentPCE.management.ParentPCEManagementSever;
import es.tid.pce.pcepsession.PCEPSessionsInformation;
import es.tid.pce.server.lspdb.ReportDB_Handler;
import es.tid.tedb.FileTEDBUpdater;
import es.tid.tedb.ITMDTEDB;
import es.tid.tedb.MDTEDB;
import es.tid.tedb.MultiDomainTEDB;
import es.tid.tedb.ReachabilityManager;
import es.tid.tedb.SimpleITTEDB;
import es.tid.tedb.SimpleTEDB;
import es.tid.tedb.TEDB;

/**
 * Parent PCE Server
 * @author ogondio
 *
 */
public class ParentPCEServer {

	/**
	 * Main class
	 * @param args
	 */
	public static void main(String[] args) {
		//First, get the parameters from the configuration file
		ParentPCEServerParameters params=new ParentPCEServerParameters();
		params.initialize();
		//Initiate the Loggers (general, PCEP Parsing, OSPF Parsing, GUI)		
		FileHandler fh;
		FileHandler fh2,fh3;
		Logger log;
		Logger log2,log3;
		log=Logger.getLogger("PCEServer");
		log2=Logger.getLogger("PCEPParser");
		log3=Logger.getLogger("OSPFParser");
		
		PCEPSessionsInformation pcepSessionManager=new PCEPSessionsInformation();
		pcepSessionManager.setLocalPcepCapability(params.getLocalPcepCapability());
		
		try {
			fh=new FileHandler(params.getParentPCEServerLogFile());
			fh2=new FileHandler(params.getParentPCEPParserLogFile());
			fh3=new FileHandler("OSPFParser.log");
			log.addHandler(fh);
			log.setLevel(Level.ALL);			
			log2.addHandler(fh2);
			log2.setLevel(Level.ALL);
			log3.addHandler(fh3);
			log3.setLevel(Level.ALL);


			Logger logGUI=Logger.getLogger("GUILogger");
			logGUI.setLevel(Level.SEVERE);
			
		} catch (Exception e1) {
			e1.printStackTrace();
			System.exit(-1);
		}
		log.info("Inizializing TID ParentPCE Server");
		//Create the Elements of the Parent PCE Server
			
		//The Traffic Engineering Database (TED)
		TEDB ted;//TEDB is generic, depending on the type of PCE, it will get a different specific TEDB
		TEDB simple_ted = null;
		LocalChildRequestManager localChildRequestManager=null;
		
		ReachabilityManager rm=new ReachabilityManager();
		
		if(!params.isITCapable()){			
			if ((params.isMultiDomain())&&(!params.isKnowsWholeTopology())){
				//The PCE is multidomain
				log.info("The PCE is multidomain");
				ted=new MDTEDB();
				((MDTEDB)ted).setReachability(rm.getReachability());
				if (params.isReadMDTEDFromFile()){
					ted.initializeFromFile(params.getMDnetworkDescriptionFile());
					((MDTEDB)ted).initializeFullTEDFromFile(params.getNetworkDescriptionFile() );

				}
			}else if (params.isKnowsWholeTopology()){
				log.info("The PCE knows the whole topology. Interdomain and intradomain");
				ted=new MDTEDB();
				((MDTEDB)ted).setReachability(rm.getReachability());
				if (params.isReadMDTEDFromFile()){
					ted.initializeFromFile(params.getMDnetworkDescriptionFile());
					((MDTEDB)ted).initializeFullTEDFromFile(params.getNetworkDescriptionFile() );

				}
				simple_ted=new SimpleTEDB();
				((SimpleTEDB)simple_ted).createGraph();
				localChildRequestManager = new LocalChildRequestManager();
				log.info("Adding Intradomain topologies to multidomain tedb");
				
				SimpleTEDB intraDomainTopologies = (SimpleTEDB)simple_ted;
				((MDTEDB)ted).setSimple_ted(intraDomainTopologies);
				
				log.info("Intradomain topologies added to multidomain tedb");
				
			}
			else{
				log.info("The PCE is single domain");
				ted=new SimpleTEDB();
				ted.initializeFromFile(params.getNetworkDescriptionFile());
				
			}
			//Read the database from a file
			MultiDomainTopologyUpdater mdt= null;
	
			if (params.isActingAsBGP4Peer()) {//BGP
				log.info("Acting as BBGP peer");
				BGPPeer bgpPeer = new BGPPeer();		
				bgpPeer.configure(params.getBGP4File());
				if (params.isMultiDomain())
					bgpPeer.setWriteMultiTEDB((MultiDomainTEDB)ted);				
				if (params.isKnowsWholeTopology())
					bgpPeer.setSimpleTEDB((SimpleTEDB)simple_ted);
				bgpPeer.createUpdateDispatcher();
				bgpPeer.startClient();		
				bgpPeer.startServer();
				bgpPeer.startManagementServer();
				bgpPeer.startSendTopology();
				
			}else{
				//Create the multidomain topology updater
				mdt=new MultiDomainTopologyUpdater((MDTEDB)ted,params.isActingAsBGP4Peer());
				mdt.initialize();
			}
			
		
			
			if (params.isMultiDomain()){
				if (params.isReadMDTEDFromFile()){
					FileTEDBUpdater.initializeReachabilityFromFile(params.getMDnetworkDescriptionFile(), rm);
				}
			}
			
			ChildPCERequestManager childPCERequestManager = new ChildPCERequestManager();
			
			//The Request Dispatcher. Incoming Requests are sent here
			//RequestQueue pathRequestsQueue;
			RequestDispatcher requestDispatcher;
			log.info("Inizializing "+ params.getChildPCERequestsProcessors()+" Path Request Processor Threads");
			//pathRequestsQueue=new RequestQueue(params.getChildPCERequestsProcessors());
			requestDispatcher=new  RequestDispatcher(params.getChildPCERequestsProcessors(),ted,null,false);
			for (int i=0;i<params.algorithmRuleList.size();++i){
				 try {
					Class aClass = Class.forName("tid.pce.computingEngine.algorithms."+params.algorithmRuleList.get(i).algoName+"Manager");
		        	log.info("Registering algorithm "+ params.algorithmRuleList.get(i).algoName+" for of = "+params.algorithmRuleList.get(i).ar.of+" and svec = "+params.algorithmRuleList.get(i).ar.svec);            
					
					if (params.algorithmRuleList.get(i).isParentPCEAlgorithm==false){
						ComputingAlgorithmManager cam= ( ComputingAlgorithmManager)aClass.newInstance();
						requestDispatcher.registerAlgorithm(params.algorithmRuleList.get(i).ar, cam);
					}
					else {
						ParentPCEComputingAlgorithmManager cam= ( ParentPCEComputingAlgorithmManager)aClass.newInstance();
						cam.setChildPCERequestManager(childPCERequestManager);
						if (params.isKnowsWholeTopology()){
							log.info("Introducimos el localChildRequestManager en la cam");
							cam.setLocalChildRequestManager(localChildRequestManager);
						}
						cam.setReachabilityManager(rm);
						requestDispatcher.registerAlgorithm(params.algorithmRuleList.get(i).ar, cam);
					}
					
				} catch (ClassNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InstantiationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			ReportDispatcher stateReportDispatcher= null;
			ReportDB_Handler rptdb = new ReportDB_Handler();
			stateReportDispatcher = new ReportDispatcher( rptdb, 2);
			if (params.getLocalPcepCapability().isStateful()){
				
//				//
//				if (pcepSessionsInformation.isStateful())
//				{
//				log.info("redis: "+params.getDbType() + " "+params.getDbName());
//					if (params.getDbType().equals("redis") && params.getDbName().length() > 0)
//					{
//						log.info("redis: redis db with id: "+ params.getDbName());
//						rptdb = new ReportDB_Handler(params.getDbName(),"localhost");	
//						rptdb.fillFromDB();
//					}
//					else
//					{
//						rptdb = new ReportDB_Handler();
//					}
//					params.setLspDB(rptdb);	
//					log.info("Creando dispatchers para el LSP DB");
//					PCCReportDispatcher = new ReportDispatcher(params, rptdb, 2);
//				}
			}
			

			log.info("Initializing Management Server");
			ParentPCEManagementSever pms=new ParentPCEManagementSever(childPCERequestManager,requestDispatcher,(MDTEDB)ted,(SimpleTEDB)simple_ted,rm,pcepSessionManager,mdt,params.getParentPCEManagementPort());		
			pms.start();
			
			//
			//System.setSecurityManager(new ParentPCESessionsControler());		
	        ServerSocket serverSocket = null;
	        boolean listening = true;
	        try {
	        	log.info("Listening on port: "+params.getParentPCEServerPort());	
	            serverSocket = new ServerSocket(params.getParentPCEServerPort());
	            //If you want to reuse the address:
	        	//InetSocketAddress local =new InetSocketAddress(Inet4Address.getByName("10.95.162.97"),params.getParentPCEServerPort() );
	        	//InetSocketAddress local =new InetSocketAddress((Inet4Address)null,params.getParentPCEServerPort() );
	        	//serverSocket = new ServerSocket();
	        	//serverSocket.setReuseAddress(true);
	        	//serverSocket.bind(local);        	
	        } catch (IOException e) {
	            System.err.println("Could not listen on port: "+params.getParentPCEServerPort());
	            System.exit(-1);
	        }

	        try {
	        	while (listening) {
	        		new ParentPCESession(serverSocket.accept(),params, requestDispatcher,ted,mdt,childPCERequestManager,rm,pcepSessionManager).start();
	        	}
	        	serverSocket.close();
	        } catch (Exception e) {
	        	e.printStackTrace();
	        }						

			
			
				
		}else{
			log.info("The PCE is IT capable");
			
			
			if (params.isMultiDomain()){
				//The PCE is multidomain
				log.info("The PCE is multidomain");
				ted=new ITMDTEDB();
				((ITMDTEDB)ted).initializeFullTEDFromFile(params.getITNetworkDescriptionFile() );
				if (params.isReadMDTEDFromFile()){
					((ITMDTEDB)ted).initializeFromFile(params.getITMDnetworkDescriptionFile());
				}
			}else {
				log.info("The PCE is single domain");
				ted=new SimpleITTEDB();
				ted.initializeFromFile(params.getITNetworkDescriptionFile());
			}
			//Read the database from a file
			
			//Create the multidomain topology updater
			MultiDomainTopologyUpdater mdt=new MultiDomainTopologyUpdater((ITMDTEDB)ted);
			mdt.ITinitialize();
				
			if (params.isMultiDomain()){
				if (params.isReadMDTEDFromFile()){
					FileTEDBUpdater.initializeReachabilityFromFile(params.getITMDnetworkDescriptionFile(), rm);
				}
			}
			
			ChildPCERequestManager childPCERequestManager = new ChildPCERequestManager();
			
			//The Request Dispatcher. Incoming Requests are sent here
			//RequestQueue pathRequestsQueue;
			RequestDispatcher requestDispatcher;
			log.info("Inizializing "+ params.getChildPCERequestsProcessors()+" Path Request Processor Threads");
			//pathRequestsQueue=new RequestQueue(params.getChildPCERequestsProcessors());
			requestDispatcher=new  RequestDispatcher(params.getChildPCERequestsProcessors(),ted,null,false);
			for (int i=0;i<params.algorithmRuleList.size();++i){
				 try {
					Class aClass = Class.forName("es.tid.pce.computingEngine.algorithms."+params.algorithmRuleList.get(i).algoName+"Manager");
		        	log.info("Registering algorithm "+ params.algorithmRuleList.get(i).algoName+" for of = "+params.algorithmRuleList.get(i).ar.of+" and svec = "+params.algorithmRuleList.get(i).ar.svec);            
					
					if (params.algorithmRuleList.get(i).isParentPCEAlgorithm==false){
						ComputingAlgorithmManager cam= ( ComputingAlgorithmManager)aClass.newInstance();
						requestDispatcher.registerAlgorithm(params.algorithmRuleList.get(i).ar, cam);
					}
					else {
						ParentPCEComputingAlgorithmManager cam= ( ParentPCEComputingAlgorithmManager)aClass.newInstance();
						cam.setChildPCERequestManager(childPCERequestManager);
						cam.setReachabilityManager(rm);
						requestDispatcher.registerAlgorithm(params.algorithmRuleList.get(i).ar, cam);
					}
					
				} catch (ClassNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InstantiationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			

			log.info("Initializing Management Server");
			ParentPCEManagementSever pms=new ParentPCEManagementSever(childPCERequestManager,requestDispatcher,(ITMDTEDB)ted,rm,pcepSessionManager,mdt,params.getParentPCEManagementPort());		
			pms.start();
			
			//
			//System.setSecurityManager(new ParentPCESessionsControler());		
	        ServerSocket serverSocket = null;
	        boolean listening = true;
	        try {
	        	log.info("Listening on port: "+params.getParentPCEServerPort());	
	            serverSocket = new ServerSocket(params.getParentPCEServerPort());
	            //If you want to reuse the address:
	        	//InetSocketAddress local =new InetSocketAddress(Inet4Address.getByName("10.95.162.97"),params.getParentPCEServerPort() );
	        	//InetSocketAddress local =new InetSocketAddress((Inet4Address)null,params.getParentPCEServerPort() );
	        	//serverSocket = new ServerSocket();
	        	//serverSocket.setReuseAddress(true);
	        	//serverSocket.bind(local);        	
	        } catch (IOException e) {
	            System.err.println("Could not listen on port: "+params.getParentPCEServerPort());
	            System.exit(-1);
	        }

	        try {
	        	pcepSessionManager.setStateful(true);
	        	while (listening) {
	        		new ParentPCESession(serverSocket.accept(),params, requestDispatcher,(ITMDTEDB)ted,mdt,childPCERequestManager,rm,pcepSessionManager).start();
	        	}
	        	serverSocket.close();
	        } catch (Exception e) {
	        	e.printStackTrace();
	        }
		}		
	}
}



