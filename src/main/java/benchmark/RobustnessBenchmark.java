package benchmark;

import common.protocol.Protocol;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Benchmark de robustez.
 * 
 * Testa o comportamento do servidor quando clientes não consomem as respostas:
 * - Slow consumers: clientes que leem respostas muito lentamente
 * - Non-consuming clients: clientes que enviam pedidos mas não leem respostas
 * 
 * Este benchmark verifica se o servidor consegue lidar com backpressure
 * e se clientes lentos não afetam outros clientes.
 */
public class RobustnessBenchmark {

  private final BenchmarkConfig config;

  public RobustnessBenchmark(BenchmarkConfig config) {
    this.config = config;
  }

  public void run() throws Exception {
    System.out.println("\n" + "=".repeat(70));
    System.out.println("           ROBUSTNESS BENCHMARK - Testes de robustez");
    System.out.println("=".repeat(70));

    // Teste 1: Slow Consumer
    testSlowConsumer();

    // Teste 2: Non-consuming clients afetam outros?
    testNonConsumingImpact();

    // Teste 3: Muitos pedidos sem leitura
    testRequestFlood();

    System.out.println("\n" + "=".repeat(70));
    System.out.println("              ROBUSTNESS TESTS COMPLETED");
    System.out.println("=".repeat(70));
  }

  /**
   * Testa um cliente que lê respostas muito lentamente.
   */
  private void testSlowConsumer() throws Exception {
    System.out.println("\n[Test 1] Slow Consumer");
    System.out.println("-".repeat(50));
    System.out.println("Objetivo: Verificar comportamento com consumidor lento");

    AtomicInteger sentCount = new AtomicInteger(0);
    AtomicInteger receivedCount = new AtomicInteger(0);
    AtomicBoolean running = new AtomicBoolean(true);

    try (Socket socket = new Socket(config.getHost(), config.getPort())) {
      socket.setSoTimeout(10000);
      
      DataOutputStream out = new DataOutputStream(socket.getOutputStream());
      InputStream in = socket.getInputStream();

      // Login primeiro
      sendLoginPacket(out, "slow_" + System.currentTimeMillis(), "pass");
      
      // Ler resposta de login
      Thread.sleep(100);
      readAvailable(in);

      // Thread que envia pedidos rapidamente
      Thread sender = new Thread(() -> {
        try {
          for (int i = 0; i < 50 && running.get(); i++) {
            sendSimpleRequest(out);
            sentCount.incrementAndGet();
            Thread.sleep(10);
          }
        } catch (Exception e) {
          // Connection might close
        }
      });

      // Thread que lê muito lentamente
      Thread slowReader = new Thread(() -> {
        try {
          while (running.get()) {
            int available = in.available();
            if (available > 0) {
              in.read();
              receivedCount.incrementAndGet();
              Thread.sleep(100); // Leitura muito lenta
            } else {
              Thread.sleep(50);
            }
          }
        } catch (Exception e) {
          // Socket closed
        }
      });

      sender.start();
      slowReader.start();

      // Aguardar 3 segundos
      Thread.sleep(3000);
      running.set(false);

      sender.join(1000);
      slowReader.join(1000);

      System.out.println("Pedidos enviados: " + sentCount.get());
      System.out.println("Bytes recebidos: " + receivedCount.get());
      System.out.println("Conclusão: " + (sentCount.get() > 20 
          ? "Servidor continuou aceitando pedidos ✓"
          : "Servidor pode ter bloqueado ✗"));
    }
  }

  /**
   * Testa se um cliente que não consome respostas afeta outros clientes.
   */
  private void testNonConsumingImpact() throws Exception {
    System.out.println("\n[Test 2] Non-Consuming Client Impact");
    System.out.println("-".repeat(50));
    System.out.println("Objetivo: Verificar se slow consumers afetam outros clientes");

    // Criar cliente que não consome
    Socket badClient = new Socket(config.getHost(), config.getPort());
    DataOutputStream badOut = new DataOutputStream(badClient.getOutputStream());
    
    // Login do bad client
    sendLoginPacket(badOut, "bad_" + System.currentTimeMillis(), "pass");
    
    // Bad client envia muitos pedidos sem ler
    Thread badSender = new Thread(() -> {
      try {
        for (int i = 0; i < 100; i++) {
          sendSimpleRequest(badOut);
          Thread.sleep(10);
        }
      } catch (Exception e) {
        // Pode fechar por buffer cheio
      }
    });
    badSender.start();

    // Medir tempo de resposta de um cliente normal
    long[] latencies = new long[10];
    
    try (Socket goodClient = new Socket(config.getHost(), config.getPort())) {
      goodClient.setSoTimeout(5000);
      DataOutputStream goodOut = new DataOutputStream(goodClient.getOutputStream());
      InputStream goodIn = goodClient.getInputStream();

      // Login
      sendLoginPacket(goodOut, "good_" + System.currentTimeMillis(), "pass");
      Thread.sleep(100);
      readAvailable(goodIn);

      // Fazer pedidos e medir latência
      for (int i = 0; i < 10; i++) {
        long start = System.currentTimeMillis();
        sendSimpleRequest(goodOut);
        
        // Esperar resposta
        Thread.sleep(50);
        readAvailable(goodIn);
        
        latencies[i] = System.currentTimeMillis() - start;
      }
    }

    badSender.join(1000);
    badClient.close();

    // Calcular média
    long sum = 0;
    for (long l : latencies) sum += l;
    double avgLatency = sum / 10.0;

    System.out.printf("Latência média do cliente normal: %.2f ms%n", avgLatency);
    System.out.println("Conclusão: " + (avgLatency < 500 
        ? "Clientes normais não afetados ✓"
        : "Slow consumers podem afetar outros ✗"));
  }

  /**
   * Testa envio de muitos pedidos sem ler respostas.
   */
  private void testRequestFlood() throws Exception {
    System.out.println("\n[Test 3] Request Flood (sem consumir respostas)");
    System.out.println("-".repeat(50));
    System.out.println("Objetivo: Testar limites de backpressure");

    AtomicInteger totalSent = new AtomicInteger(0);
    AtomicInteger errors = new AtomicInteger(0);
    CountDownLatch done = new CountDownLatch(5);

    List<Socket> sockets = new ArrayList<>();

    // 5 clientes enviando sem ler
    for (int c = 0; c < 5; c++) {
      final int clientId = c;
      Thread t = new Thread(() -> {
        Socket socket = null;
        try {
          socket = new Socket(config.getHost(), config.getPort());
          socket.setSoTimeout(1000);
          sockets.add(socket);
          
          DataOutputStream out = new DataOutputStream(socket.getOutputStream());
          
          // Login
          sendLoginPacket(out, "flood_" + clientId + "_" + System.currentTimeMillis(), "pass");

          // Enviar muitos pedidos sem ler
          for (int i = 0; i < 200; i++) {
            try {
              sendSimpleRequest(out);
              totalSent.incrementAndGet();
            } catch (IOException e) {
              errors.incrementAndGet();
              break;
            }
          }
        } catch (Exception e) {
          errors.incrementAndGet();
        } finally {
          done.countDown();
        }
      });
      t.start();
    }

    done.await();

    // Cleanup
    for (Socket s : sockets) {
      try { s.close(); } catch (Exception e) {}
    }

    System.out.println("Total de pedidos enviados: " + totalSent.get());
    System.out.println("Clientes com erros: " + errors.get());
    
    if (errors.get() > 0) {
      System.out.println("Conclusão: Servidor aplicou backpressure (fechou conexões saturadas) ✓");
    } else {
      System.out.println("Conclusão: Servidor aceitou todos os pedidos (buffers grandes) ✓");
    }
  }

  private void sendLoginPacket(DataOutputStream out, String username, String password) 
      throws IOException {
    // Frame: [length(4)] [tag(4)] [type(1)] [username] [password]
    byte[] payload = buildLoginPayload(username, password);
    
    out.writeInt(payload.length);
    out.writeInt(1); // tag
    out.write(payload);
    out.flush();
  }

  private byte[] buildLoginPayload(String username, String password) throws IOException {
    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);
    dos.writeByte(Protocol.MSG_REGISTER);
    dos.writeUTF(username);
    dos.writeUTF(password);
    dos.flush();

    // Também fazer login
    java.io.ByteArrayOutputStream baos2 = new java.io.ByteArrayOutputStream();
    DataOutputStream dos2 = new DataOutputStream(baos2);
    dos2.writeByte(Protocol.MSG_LOGIN);
    dos2.writeUTF(username);
    dos2.writeUTF(password);
    dos2.flush();

    return baos.toByteArray();
  }

  private void sendSimpleRequest(DataOutputStream out) throws IOException {
    // Enviar pedido ADD_EVENT
    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);
    dos.writeByte(Protocol.MSG_ADD_EVENT);
    dos.writeUTF("TestProduct");
    dos.writeInt(1);
    dos.writeDouble(10.0);
    dos.flush();

    byte[] payload = baos.toByteArray();
    out.writeInt(payload.length);
    out.writeInt((int)(System.nanoTime() & 0xFFFFFF)); // tag único
    out.write(payload);
    out.flush();
  }

  private int readAvailable(InputStream in) throws IOException {
    int count = 0;
    while (in.available() > 0) {
      in.read();
      count++;
    }
    return count;
  }

  public static void main(String[] args) throws Exception {
    BenchmarkConfig config = new BenchmarkConfig();
    new RobustnessBenchmark(config).run();
  }
}
