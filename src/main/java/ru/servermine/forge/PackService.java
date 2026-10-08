package ru.servermine.forge;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

final class PackService implements AutoCloseable {
    private final ForgeSystemPlugin plugin;
    private HttpServer server;private ExecutorService executor;
    private byte[] hash;private UUID id;private final Set<UUID> loaded=new HashSet<>();
    PackService(ForgeSystemPlugin plugin){this.plugin=plugin;}
    void start() throws Exception {
        var c=plugin.settings.pack;id=UUID.fromString(c.getString("uuid"));if(!c.getBoolean("enabled"))return;
        Path file=plugin.getDataFolder().toPath().resolve("forge.zip");
        try(var in=Objects.requireNonNull(plugin.getResource("forge.zip"))){Files.copy(in,file,StandardCopyOption.REPLACE_EXISTING);}
        byte[] zip=Files.readAllBytes(file);hash=MessageDigest.getInstance("SHA-1").digest(zip);
        server=HttpServer.create(new InetSocketAddress(c.getString("host"),c.getInt("port")),0);
        server.createContext("/forge.zip",exchange->{try{if(!exchange.getRequestURI().getPath().equals("/forge.zip")||!Set.of("GET","HEAD").contains(exchange.getRequestMethod())){exchange.sendResponseHeaders(404,-1);return;}
            exchange.getResponseHeaders().set("Content-Type","application/zip");exchange.getResponseHeaders().set("Cache-Control","no-cache");
            if(exchange.getRequestMethod().equals("HEAD")){exchange.getResponseHeaders().set("Content-Length",Integer.toString(zip.length));exchange.sendResponseHeaders(200,-1);}else{exchange.sendResponseHeaders(200,zip.length);exchange.getResponseBody().write(zip);}}finally{exchange.close();}});
        executor=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"ForgeSystem-pack");t.setDaemon(true);return t;});server.setExecutor(executor);server.start();
    }
    boolean loaded(Player p){return loaded.contains(p.getUniqueId());}
    void send(Player p){if(server!=null)p.addResourcePack(id,plugin.settings.pack.getString("url"),hash,"Интерфейс кузницы ServerMine",plugin.settings.pack.getBoolean("required"));}
    void status(PlayerResourcePackStatusEvent e){if(!e.getID().equals(id))return;if(e.getStatus()==PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED)loaded.add(e.getPlayer().getUniqueId());else loaded.remove(e.getPlayer().getUniqueId());}
    void quit(Player p){loaded.remove(p.getUniqueId());}
    public void close(){if(server!=null)server.stop(0);if(executor!=null)executor.shutdownNow();}
}
