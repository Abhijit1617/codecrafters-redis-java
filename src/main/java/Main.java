import Components.Server.MasterTcpServer;
import Components.Server.RedisConfig;
import Components.Server.SlaveTcpServer;
import Config.AppConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(AppConfig.class);

        MasterTcpServer master = context.getBean(MasterTcpServer.class);
        SlaveTcpServer slave = context.getBean(SlaveTcpServer.class);
        RedisConfig redisConfig = context.getBean(RedisConfig.class);

        int port = 6379;
        redisConfig.setPort(port);
        redisConfig.setRole("master");

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Missing value for --port");
                    }
                    port = Integer.parseInt(args[++i]);
                    redisConfig.setPort(port);
                    break;

                case "--replicaof":
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Missing value for --replicaof");
                    }

                    redisConfig.setRole("slave");
                    String replicaOf = args[++i].trim();
                    String[] masterInfo = replicaOf.split("\\s+");

                    if (masterInfo.length != 2) {
                        throw new IllegalArgumentException(
                                "--replicaof expects: <MASTER_HOST> <MASTER_PORT>"
                        );
                    }

                    redisConfig.setMasterHost(masterInfo[0]);
                    redisConfig.setMasterPort(Integer.parseInt(masterInfo[1]));
                    break;

                default:
                    System.out.println("Unknown argument: " + args[i]);
                    break;
            }
        }

        System.out.println(
                "Role: " + redisConfig.getRole()
                        + " | Port: " + redisConfig.getPort()
        );

        if ("slave".equals(redisConfig.getRole())) {
            System.out.println(
                    "Master: "
                            + redisConfig.getMasterHost()
                            + ":"
                            + redisConfig.getMasterPort()
            );
            slave.startServer();
        } else {
            master.startServer();
        }
    }
}
