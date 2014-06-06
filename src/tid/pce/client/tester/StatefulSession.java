package tid.pce.client.tester;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.Socket;
import java.util.LinkedList;

import tid.pce.client.PCCPCEPSession;
import tid.pce.pcep.messages.PCEPInitiate;
import tid.pce.pcep.messages.PCEPMessage;
import tid.pce.pcep.messages.PCEPMessageTypes;
import tid.pce.pcep.messages.PCEPReport;
import tid.pce.pcep.objects.EndPointsIPv4;
import tid.pce.pcep.objects.ExplicitRouteObject;
import tid.pce.pcep.objects.LSP;
import tid.pce.pcep.objects.PCEPIntiatedLSP;
import tid.pce.pcep.objects.SRP;
import tid.pce.pcepsession.PCEPSessionsInformation;
import tid.rsvp.objects.subobjects.IPv4prefixEROSubobject;
import tid.util.UtilsFunctions;
import tid.vntm.client.VNTMClientSession;

public class StatefulSession {
	
	
	public static void main (String[] args) {

		try {
				Runtime.getRuntime().exec("clear");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	      BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

	      System.out.println("Statefull PCE Session");
	      System.out.println("Please get PCEPSession Info:");
	      System.out.print("PCE IP:");



	      String ip = null;
	      try {
	         ip = br.readLine();
	      } catch (IOException ioe) {
	         System.out.println("IO error");
	         System.exit(1);
	      }

	      System.out.print("PCE Port:");



	      String port = null;
	      int portnumber=0;
	      try {
	         port = br.readLine();
	         portnumber=Integer.parseInt(port);
	      } catch (IOException ioe) {
	         System.out.println("IO error");
	         System.exit(1);
	      }

	      System.out.print("\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n");
	      StatefulPCEPSession vntmsession=null;
			
			try {
				Socket s = new Socket(ip, portnumber);
				PCEPSessionsInformation pcepsessioninfo=new PCEPSessionsInformation();
				pcepsessioninfo.setActive(true);
				pcepsessioninfo.setStateful(false);
				vntmsession = new StatefulPCEPSession( s,pcepsessioninfo, PCEPMessageTypes.MESSAGE_INTIATE);
				vntmsession.start();
				}
			catch(Exception e){
				e.printStackTrace();
			}
	      
	   }

	}

