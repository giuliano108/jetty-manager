package bz._108;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.tools.attach.*;
import org.docopt.Docopt;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import static java.lang.management.ManagementFactory.*;

public final class JettyManager {
    private static final String doc = "sudo -u username jetty-manager\n"
            + "\n"
            + "Usage:\n"
            + "  jetty-manager jvms\n"
            + "  jetty-manager webapps <jvm> [ <webappfilter> ]\n"
            + "  jetty-manager threads <jvm>\n"
            + "  jetty-manager stop <jvm> <webappfilter>\n"
            + "  jetty-manager start <jvm> <webappfilter>\n"
            + "  jetty-manager restart <jvm> <webappfilter>\n"
            + "  jetty-manager (-h | --help)\n"
            + "\n"
            + "Commands:\n"
            + "  jvms           Show the running JVMs (PID, name)\n"
            + "  webapps        Show the webapps hosted by <jvm> and their state\n"
            + "  threads        Show the total number of threads in the <jvm>\n"
            + "\n"
            + "Arguments:\n"
            + "  <jvm>          JVM PID or regexp (matched against the JVM name)\n"
            + "  <webappfilter> regexp matched against the context path (URL)\n"
            + "\n"
            + "Options:\n"
            + "  -h --help     Show this screen\n"
            + "\n";

    static final String CONNECTOR_ADDRESS_PROPERTY = "com.sun.management.jmxremote.localConnectorAddress";
    static enum WebappOp { STOP, START, RESTART }

    static List<VirtualMachineDescriptor> vms;
    static VirtualMachine attachedVm;
    static JMXConnector jmxConnector;
    static MBeanServerConnection mbeanConn;
    static Set<ObjectName> webapps = new HashSet<ObjectName>();

    static boolean partialRegexMatch(String string, String regex) {
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(string);
        return m.find();
    }

    static void getJvms() throws NoJVMsFound {
        vms = VirtualMachine.list();
        String thisVmName = getRuntimeMXBean().getName();
        String thisVmId;
        if (!thisVmName.contains("@")) {
            thisVmId = "";
        } else {
            thisVmId = thisVmName.split("@")[0];
        }
        if (vms.isEmpty()) throw new NoJVMsFound();

        // Remove the JVM jetty-manager is running on from the list
        Iterator<VirtualMachineDescriptor> iter = vms.iterator();
        while (iter.hasNext()) {
            VirtualMachineDescriptor vm = iter.next();
            if (vm.id().equals(thisVmId)) iter.remove();
        }
        if (vms.isEmpty()) throw new NoJVMsFound();
    }

    static void attachVm(String pidOrNameRegexp) throws CannotAttachToJVM, IOException, AttachNotSupportedException {
        boolean isPid = pidOrNameRegexp.matches("^\\d+$");
        VirtualMachineDescriptor vmdesc = null;
        for (VirtualMachineDescriptor desc : vms) {
            VirtualMachine vm;
            if(isPid && desc.id().equals(pidOrNameRegexp)) {
                vmdesc = desc;
                break;
            } else if(!isPid && partialRegexMatch(desc.displayName(), pidOrNameRegexp)) {
                vmdesc = desc;
                break;
            }
        }
        if (vmdesc != null) {
            System.out.println(String.format("Attaching to PID %s - %s", vmdesc.id(), vmdesc.displayName()));
            attachedVm = VirtualMachine.attach(vmdesc);
        } else {
            throw new CannotAttachToJVM();
        }
    }

    static void getMBeanServerConnection() throws IOException, AgentLoadException, AgentInitializationException {
        Properties props = null;
        props = attachedVm.getAgentProperties();
        String connectorAddress = props.getProperty(CONNECTOR_ADDRESS_PROPERTY);
        if (connectorAddress == null) {
            props = attachedVm.getSystemProperties();
            String home = props.getProperty("java.home");
            String agent = home + File.separator + "lib" + File.separator + "management-agent.jar";
            attachedVm.loadAgent(agent);
            props = attachedVm.getAgentProperties();
            connectorAddress = props.getProperty(CONNECTOR_ADDRESS_PROPERTY);
        }
        JMXServiceURL url = new JMXServiceURL(connectorAddress);
        jmxConnector = JMXConnectorFactory.connect(url);
        mbeanConn = jmxConnector.getMBeanServerConnection();
    }

    static void closeMBeanServerConnection() throws IOException {
        jmxConnector.close();
    }

    static void printJvms() {
        System.out.println(String.format("%-10s %s","JVM","DisplayName"));
        System.out.println(String.format("%-10s %s","---","---"));
        for(VirtualMachineDescriptor vm : vms) {
            System.out.println(String.format("%-10s %s",vm.id(),vm.displayName()));
        }
    }

    static void printThreads() throws MalformedObjectNameException, AttributeNotFoundException, MBeanException, ReflectionException, InstanceNotFoundException, IOException {
            Object threadCount = mbeanConn.getAttribute(new ObjectName(ManagementFactory.THREAD_MXBEAN_NAME), "ThreadCount");
            System.out.println(String.format("ThreadCount: %d",(Integer) threadCount));
    }

    static void getWebapps(String regexFilter) throws MalformedObjectNameException, IOException, AttributeNotFoundException, MBeanException, ReflectionException, InstanceNotFoundException {
        ObjectName webappsContext = new ObjectName("org.eclipse.jetty.webapp:context=*,type=webappcontext,id=*");
        Set<ObjectName> webappsContexts = mbeanConn.queryNames(webappsContext,null);
        for (ObjectName objectName : webappsContexts) {
            String contextPath = (String) mbeanConn.getAttribute(objectName, "contextPath");
            if(partialRegexMatch(contextPath, regexFilter)) {
                webapps.add(objectName);
            }
        }
    }

    static String fixWebappFilter(Map<String, Object> opts) {
        String filter = ".*";
        if (opts.get("<webappfilter>") != null) {
            filter = (String) opts.get("<webappfilter>");
        }
        return filter;
    }

    static void printWebapps() throws AttributeNotFoundException, MBeanException, ReflectionException, InstanceNotFoundException, IOException {
        for (ObjectName objectName : webapps) {
            String displayName = (String) mbeanConn.getAttribute(objectName, "displayName");
            String contextPath = (String) mbeanConn.getAttribute(objectName, "contextPath");
            String state = (String) mbeanConn.getAttribute(objectName, "state");
            System.out.println();
            System.out.println(displayName);
            System.out.println(contextPath);
            System.out.println(state);
        }
    }

    static List<WebappOp> getWebappOps(Map<String, Object> opts) {
        List<WebappOp> webappOps = new ArrayList<WebappOp>();
        if ((Boolean) opts.get("stop")) {
            webappOps.add(WebappOp.STOP);
        } else if ((Boolean) opts.get("start")) {
            webappOps.add(WebappOp.START);
        } else if ((Boolean) opts.get("restart")) {
            webappOps.add(WebappOp.STOP);
            webappOps.add(WebappOp.START);
        } else {
            throw new IllegalArgumentException();
        }
        return webappOps;
    }

    static void pokeWebapps(List<WebappOp> webappOps) throws AttributeNotFoundException, MBeanException, ReflectionException, InstanceNotFoundException, IOException {
        for (ObjectName objectName : webapps) {
            String contextPath = (String) mbeanConn.getAttribute(objectName, "contextPath");
            for (WebappOp op : webappOps) {
                System.out.println(String.format("Calling %s on %s", op, contextPath));
                if (op == WebappOp.STOP) {
                    mbeanConn.invoke(objectName,"stop",null,null);
                } else if (op == WebappOp.START) {
                    mbeanConn.invoke(objectName,"start",null,null);
                } else {
                    throw new IllegalArgumentException();
                }
            }
        }
    }

    public static void main(final String[] args) {
        final Map<String, Object> opts = new Docopt(doc).parse(args);

        try {
            getJvms();
            if ((Boolean) opts.get("jvms")) {
                printJvms();
            } else if ((Boolean) opts.get("webapps")) {
                attachVm((String)opts.get("<jvm>"));
                getMBeanServerConnection();
                getWebapps(fixWebappFilter(opts));
                printWebapps();
                closeMBeanServerConnection();
            } else if ((Boolean) opts.get("threads")) {
                attachVm((String)opts.get("<jvm>"));
                getMBeanServerConnection();
                printThreads();
                closeMBeanServerConnection();
            } else {
                attachVm((String)opts.get("<jvm>"));
                getMBeanServerConnection();
                getWebapps(fixWebappFilter(opts));
                List<WebappOp> webappOps = getWebappOps(opts);
                pokeWebapps(webappOps);
                closeMBeanServerConnection();
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Remember to run this as the user that owns the JVM: sudo -u username jetty-manager ...");
            System.exit(255);
        }
    }
}
