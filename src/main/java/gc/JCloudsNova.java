package gc;

import static com.google.common.io.Closeables.closeQuietly;

import java.io.Closeable;
import java.io.*;
import java.util.Set;

import org.jclouds.collect.PagedIterable;
import org.jclouds.ContextBuilder;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.NovaAsyncApi;
import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;
import org.jclouds.openstack.nova.v2_0.extensions.VmsApi;
import org.jclouds.openstack.nova.v2_0.extensions.QuotaClassApi;
import org.jclouds.rest.RestContext;
import org.jclouds.openstack.v2_0.domain.Extension;
import org.jclouds.openstack.v2_0.features.ExtensionApi;
import org.jclouds.openstack.v2_0.domain.Resource;
import org.jclouds.openstack.nova.v2_0.domain.ServerCreated;
import org.jclouds.openstack.nova.v2_0.options.LaunchServerOptions;
import org.jclouds.compute.domain.NodeMetadata;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.FluentIterable;
import com.google.inject.Module;

public class JCloudsNova implements Closeable {
   private ComputeService compute;
   private RestContext<NovaApi, NovaAsyncApi> nova;
   private Set<String> zones;

   public static void main(String[] args) {
      JCloudsNova jCloudsNova = new JCloudsNova();

      try {
         jCloudsNova.init();
         jCloudsNova.listServers();
         // jCloudsNova.checkExtension();
         //jCloudsNova.testVms();
         jCloudsNova.close();
      }
      catch (Exception e) {
         e.printStackTrace();
      }
      finally {
         jCloudsNova.close();
      }
   }

   private void init() {
      Iterable<Module> modules = ImmutableSet.<Module> of(new SLF4JLoggingModule());

      String provider = "openstack-nova";
      String identity = "rui:rui"; // tenantName:userName
      String password = "mergegrid"; // demo account uses ADMIN_PASSWORD too

      ComputeServiceContext context = ContextBuilder.newBuilder(provider)
            .endpoint("http://10.1.1.1:5000/v2.0")
            .credentials(identity, password)
            .modules(modules)
            .buildView(ComputeServiceContext.class);
      compute = context.getComputeService();
      nova = context.unwrap();
      zones = nova.getApi().getConfiguredZones();
   }
   private void checkExtension() {
       for (String zone: zones) {
           /*
           Optional<? extends FloatingIPApi> apiOption = nova.getApi().getFloatingIPExtensionForZone(zone);
           */
           /*Optional<? extends QuotaClassApi> apiOption = nova.getApi().getQuotaClassExtensionForZone(zone);
           if (!apiOption.isPresent()) {
               System.out.println("extension not found.");
               continue;
           }
           System.out.println("found extension.");*/
           ExtensionApi apiOption = nova.getApi().getExtensionApiForZone(zone);
           System.out.println ("listing extensions: ");
           for (Extension ext : apiOption.list()) {
               
               System.out.format ("%s %s %s %s %s//%s\n", ext.getName(), ext.getNamespace(), ext.getAlias(), ext.getUpdated(), ext.getDescription(), ext.toString());
           }
       }
   }

   private void testVms() {
       System.out.println("\nStarting vms test..\n");
       for (String zone: zones) {
           ServerApi serverApi = nova.getApi().getServerApiForZone(zone);
           Optional<? extends VmsApi> vmsApiOption = nova.getApi().getVmsExtensionForZone(zone);
           if (!vmsApiOption.isPresent()) {
               System.out.println("Did not find vms extension for zone " + zone);
               continue;
           }
           VmsApi vmsapi = vmsApiOption.get();
           System.out.println("Got vms extension for zone " +zone);

           final String origUuid = "478260d2-cd2c-4f43-b95c-4c3356560cc9";
           System.out.format("creating live image from instance %s\n", origUuid); 
           FluentIterable<? extends ServerCreated> s = vmsapi.liveImageCreate(origUuid, "jclouds-test-bless");
           System.out.println(s.toString());
           wait(25);
           System.out.format("starting live image w userdata %s\n", s.first().get().getId());

           //try {
               //byte[] userdata = IOUtil.readFile("/home/rui/testjclouds/jclouds-vms-test/testuserdata");
               byte[] userdata = "#!/bin/bash\ntouch ~/hithere".getBytes();
               LaunchServerOptions options = new LaunchServerOptions().userData(userdata);

               FluentIterable<? extends ServerCreated> a = vmsapi.liveImageStart(s.first().get().getId(),"jclouds-test-launch-options", options);
               System.out.println(a.toString());
           /*} catch (IOException e) {
               System.out.println("cannot find file testuserdata"); 
           }*/
           try {
               System.out.format("testing delete live image %s has error .. ", s.first().get().getId());
               boolean b = vmsapi.liveImageDelete(s.first().get().getId());
               System.out.println ("FAILED.\nstatus: " + new Boolean(b).toString());
           } catch (Exception e) {System.out.println("PASSED.");}
           //System.out.format("deleting launched instance %s\n", a.first().get().getId());
           //serverApi.delete(a.first().get().getId());
           System.out.println("launching 3 clones ..");
           FluentIterable<? extends ServerCreated> tclones = vmsapi.liveImageStart(s.first().get().getId(),"jclouds-test-launch3",
                   new LaunchServerOptions().numInstances(3));
           System.out.println(tclones.toString());

           System.out.println ("listing live images of " + origUuid);
           System.out.println(vmsapi.liveImageList(origUuid).toString());
           System.out.println ("listing launched instances of " + s.first().get().getId());
           FluentIterable<? extends Server> allclonesofs = vmsapi.liveImageServers(s.first().get().getId());
           System.out.println (allclonesofs.toString());
           /*for (Server c : allclonesofs) {
               System.out.format(" deleting %s\n", c.getId());
               serverApi.delete(c.getId());
           }*/
           wait(10);
           System.out.format("deleting live image %s\n", s.first().get().getId());
           boolean b = vmsapi.liveImageDelete(s.first().get().getId());
           System.out.println ("status: " + new Boolean(b).toString());
       }
   }

   private void wait(int s) {
       System.out.format("Waiting %s s\n", s);
       try { Thread.sleep(s*1000); } catch (Exception e) {}
   }

   private void listServers() {
      for (String zone: zones) {
         ServerApi serverApi = nova.getApi().getServerApiForZone(zone);

         System.out.println("Servers in " + zone);

         // was Server server: serverApi.listInDetail()..
         for (Resource server: serverApi.list().concat()) {
             if (server.getName().equals("ivy-one")) {
               System.out.println("  " + server);
             } else {
                 System.out.format("%s - %s\n",server.getName(),server.getId());
             }
         }
         /* // Success
         System.out.println("Booting");
         ServerCreated s = serverApi.create("test1","275d367a-82a2-4f9e-8338-ec937be30c76","1");
         System.out.format("result: %s\n", s);*/
         //NodeMetadata node = compute.getNodeMetadata(zone + "/8e2d763a-c392-409f-bca1-e6f579646e00");
         //System.out.println(node.toString());
         //System.out.println(serverApi.delete("82c3d606-ad2e-4213-bf60-52ee3b6a0438"));
      }
   }

   public void close() {
      closeQuietly(compute.getContext());
   }

    private static class IOUtil {

        public static byte[] readFile(String file) throws IOException {
            return readFile(new File(file));
        }

        public static byte[] readFile(File file) throws IOException {
            // Open file
            RandomAccessFile f = new RandomAccessFile(file, "r");
            try {
                // Get and check length
                long longlength = f.length();
                int length = (int) longlength;
                if (length != longlength)
                    throw new IOException("File size >= 2 GB");
                // Read file and return data
                byte[] data = new byte[length];
                f.readFully(data);
                return data;
            } finally {
                f.close();
            }
        }
    }

}
