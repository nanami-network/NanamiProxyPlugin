package xyz.n7mn.dev.nanamiproxyplugin;

import com.amihaiemil.eoyaml.Yaml;
import com.amihaiemil.eoyaml.YamlMapping;
import com.amihaiemil.eoyaml.YamlMappingBuilder;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.util.Favicon;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import xyz.n7mn.dev.nanamiproxyplugin.JsonData.ReceiveData;
import xyz.n7mn.dev.nanamiproxyplugin.JsonData.SendData;
import xyz.n7mn.dev.nanamiproxyplugin.ServerData.ServerList;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

@Plugin(
        id = "nanamiproxyplugin",
        name = "NanamiProxyPlugin",
        version = BuildConstants.VERSION,
        description = "VelocityPlayerSyncPlugin",
        url = "https://twitter.com/7mi_network",
        authors = {"7mi_chan"}
)
public class Nanamiproxyplugin {

    @Inject
    private Logger logger;

    @Inject
    private ProxyServer proxyServer;

    private Optional<PluginContainer> plugin;

    private HashMap<String, ReceiveData> ProxyServerList = new HashMap<>();

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {

        plugin = proxyServer.getPluginManager().getPlugin("nanamiproxyplugin");

        File file1 = new File("./plugins/" + plugin.get().getDescription().getName().get());
        File file2 = new File("./plugins/" + plugin.get().getDescription().getName().get() + "/server-sample.7mi.xyz.yml");
        File file3 = new File("./plugins/" + plugin.get().getDescription().getName().get() + "/config.yml");
        if (!file1.exists()){
            file1.mkdir();
        }

        if (!file2.exists()){

            YamlMappingBuilder builder = Yaml.createYamlMappingBuilder();
            YamlMapping mapping = builder.add(
                    "ProxyName", "Sample"
            ).add(
                    "MinProtocolVer", "47"
            ).add(
                    "MaxProtocolVer", "758"
            ).add(
                    "VersionText","NanamiProxySystem 2.0"
            ).add(
                    "ServerGroup","Sample"
            ).add(
                    "ServerID","0"
            ).add(
                    "ServerName","Sample"
            ).add(
                    "ServerText","????????????????????????????????? ???sample.7mi.xyz?????????????????????????????????????????????????????????????????????"
            ).add(
                    "ServerMaxPlayers","100"
            ).build();

            String yml = mapping.toString();

            try {
                PrintWriter writer = new PrintWriter(file2);
                writer.print(yml);
                writer.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        boolean NewConfig = false;
        if (!file3.exists()){
            YamlMappingBuilder builder = Yaml.createYamlMappingBuilder();
            YamlMapping mapping = builder.add(
                    "ServerIP", "localhost"
            ).add(
                    "ServerPort", "26666"
            ).build();

            String yml = mapping.toString();

            try {
                PrintWriter writer = new PrintWriter(file3);
                writer.print(yml);
                writer.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            NewConfig = true;
        }


        if (NewConfig){
            logger.info("config.yml?????????????????????????????????");
            return;
        }


        // ????????????????????????????????????????????????????????????????????????????????????????????????
        File ConfigFile = new File("./plugins/" + plugin.get().getDescription().getName().get() + "/config.yml");
        if (!ConfigFile.exists()){
            return;
        }
        YamlMapping ConfigYaml = null;
        try {
            ConfigYaml = Yaml.createYamlInput(ConfigFile).readYamlMapping();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // ?????????????????????????????????
        Timer timer = new Timer();
        YamlMapping finalConfigYaml = ConfigYaml;
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                new Thread(()->{

                    // ??????????????????????????????????????????
                    //logger.info("????????????????????????????????????????????????...");
                    File[] files = new File("./plugins/" + plugin.get().getDescription().getName().get()).listFiles();
                    List<ServerList> list = new ArrayList<>();

                    Map<String, Boolean> temp = new HashMap<>();
                    for (File file : files){
                        if (!file.getName().startsWith("server-") || file.getName().startsWith("server-sample")){
                            continue;
                        }

                        try {
                            YamlMapping mapping = Yaml.createYamlInput(file).readYamlMapping();
                            String group = mapping.string("ServerGroup");
                            String serverName = mapping.string("ServerName");
                            int serverID = mapping.integer("ServerID");
                            int playerCount = 0;
                            String[] playerUUIDList = new String[0];
                            String[] playerNameList = new String[0];

                            Optional<RegisteredServer> server = proxyServer.getServer(mapping.string("ProxyName"));
                            if (server.isPresent()){
                                Collection<Player> players = server.get().getPlayersConnected();

                                String[] temp1 = new String[players.size()];
                                String[] temp2 = new String[players.size()];

                                for (Player player : players){
                                    temp1[playerCount] = player.getUniqueId().toString();
                                    temp2[playerCount] = player.getUsername();

                                    playerCount++;
                                }

                                playerUUIDList = temp1;
                                playerNameList = temp2;
                            }

                            if (temp.get(mapping.string("ProxyName")) != null){
                                playerUUIDList = new String[0];
                                playerNameList = new String[0];
                            } else {
                                temp.put(mapping.string("ProxyName"), true);
                            }
                            list.add(new ServerList(group, serverID, serverName, playerCount, playerUUIDList, playerNameList));
                        } catch (IOException e) {
                            e.printStackTrace();
                            return;
                        }
                    }

                    //logger.info("????????????????????????????????????????????? " + list.size());

                    // ??????????????????
                    ProxyServerList.clear();
                    for (ServerList server : list){
                        // ???????????????????????????
                        SendData data = new SendData(server.getGroupName(), server.getServerID(), server.getServerName(), server.getPlayerUUIDList(), server.getPlayerNameList());

                        // TCP????????????
                        try {
                            //logger.info("??????????????????????????????...");
                            Socket socket = new Socket(finalConfigYaml.string("ServerIP"), finalConfigYaml.integer("ServerPort"));

                            OutputStream outputStream = socket.getOutputStream();
                            String json = new Gson().toJson(data);

                            outputStream.write(json.getBytes(StandardCharsets.UTF_8));
                            outputStream.flush();

                            //logger.info("?????????????????????????????? ?????????????????????????????????...");
                            byte[] receiveData = new byte[52428800];
                            InputStream inputStream = socket.getInputStream();
                            int i = inputStream.read(receiveData);
                            receiveData = Arrays.copyOf(receiveData, i);
                            ReceiveData fromJson = new Gson().fromJson(new String(receiveData, StandardCharsets.UTF_8), ReceiveData.class);

                            //logger.info("??????????????????????????????????????????????????????");
                            ProxyServerList.put(fromJson.getGroupName(), fromJson);
                            //logger.info(fromJson.getGroupName() + "?????????????????????????????????");

                            inputStream.close();
                            outputStream.close();
                            socket.close();

                            try {
                                Thread.sleep(500L);
                            } catch (Exception e){
                                e.printStackTrace();
                            }

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                }).start();

            }
        };

        timer.scheduleAtFixedRate(task, 0L, 1000L);

    }

    @Subscribe
    public void ProxyPingEvent(ProxyPingEvent e){
        String hostName = e.getConnection().getVirtualHost().get().getHostName();
        ServerPing ping = e.getPing();
        ServerPing.Builder builder = ping.asBuilder();
        Component text = ping.getDescriptionComponent();

        try {

            File file1 = new File("./plugins/" + plugin.get().getDescription().getName().get() + "/server-" + hostName + ".yml");

            if (file1.exists()){
                YamlMapping mapping = Yaml.createYamlInput(file1).readYamlMapping();

                ReceiveData data = ProxyServerList.get(mapping.string("ServerGroup"));
                if (data != null){
                    builder.onlinePlayers(data.getPlayerCount());

                    List<ServerPing.SamplePlayer> players = new ArrayList<>();
                    for (int i = 0; i < data.getPlayerCount(); i++){
                        ServerPing.SamplePlayer player = new ServerPing.SamplePlayer(data.getPlayerList()[i], UUID.fromString(data.getPlayerUUIDList()[i]));
                        players.add(player);
                    }

                    builder.samplePlayers(players.toArray(ServerPing.SamplePlayer[]::new));
                }
                builder.maximumPlayers(mapping.integer("ServerMaxPlayers"));

                String desc = mapping.string("ServerText");
                String verText = mapping.string("VersionText");

                if (desc != null){
                    text = Component.text(desc);
                }

                if (verText != null){

                    int minProtocolVer = Integer.parseInt(mapping.string("MinProtocolVer"));
                    int maxProtocolVer = Integer.parseInt(mapping.string("MaxProtocolVer"));

                    if (e.getConnection().getProtocolVersion().getProtocol() >= minProtocolVer && e.getConnection().getProtocolVersion().getProtocol() <= maxProtocolVer){
                        builder.version(new ServerPing.Version(e.getConnection().getProtocolVersion().getProtocol(), verText));
                    } else if (e.getConnection().getProtocolVersion().getProtocol() >= maxProtocolVer){
                        builder.version(new ServerPing.Version(maxProtocolVer, verText));
                    } else if (e.getConnection().getProtocolVersion().getProtocol() <= minProtocolVer){
                        builder.version(new ServerPing.Version(minProtocolVer, verText));
                    } else {
                        builder.version(new ServerPing.Version(maxProtocolVer, verText));
                    }
                    
                }
            }


            //String url = ping.getFavicon().get().getBase64Url();
            //System.out.println("icon : "+url);

            File file2 = new File("./plugins/" + plugin.get().getDescription().getName().get() + "/" + hostName + ".png");
            //System.out.println(file.toPath());

            if (file2.exists()){
                //System.out.println("!!");
                String contentType = Files.probeContentType(file2.toPath());

                StringBuilder sb = new StringBuilder();
                sb.append("data:");
                sb.append(contentType);
                sb.append(";base64,");
                sb.append(Base64.getEncoder().encodeToString(Files.readAllBytes(file2.toPath())));

                Favicon favicon = new Favicon(sb.toString());
                //System.out.println(sb.toString());
                builder.favicon(favicon);
            }

            builder.description(text);
            e.setPing(builder.build());

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}