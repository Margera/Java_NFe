
package br.com.swconsultoria.nfe.threadsafe;

import java.nio.file.Paths;
import java.util.Objects;
import java.lang.reflect.Field;
import java.net.URI;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.junit.jupiter.api.Test;
import br.com.swconsultoria.certificado.Certificado;
import br.com.swconsultoria.certificado.CertificadoService;
import br.com.swconsultoria.certificado.exception.CertificadoException;
import lombok.extern.java.Log;

/**
 * Classe de teste para verificar a concorrência entre threads.
 * 
 * Esta classe contém métodos para carregar certificados, verificar protocolos e realizar testes de concorrência.
 * Os testes são realizados com certificados diferentes, utilizando threads para simular a execução concorrente.
 * 
 * O ponto principal dos testes é mostrar que existe um compartilhamento de dados do Protocol(org.apache.commons.httpclient.protocol.Protocol)
 * que é configurado na lib br.com.swconsultoria.certificado.CertificadoService.
 * 
 * O Protocol pode ser usado na configuração do client em br.com.swconsultoria.nfe.wsdl.NFeDistribuicaoDFe.NFeDistribuicaoDFeStub o que pode
 * estar causando o problema de concorrência.
 */
@Log
final class ThreadSafeTest {

    /**
     * Carrega um certificado a partir de um arquivo PFX.
     *
     * @param nomeArquivo O nome do arquivo PFX contendo o certificado.
     * @param senha A senha para acessar o certificado.
     * @return O objeto Certificado carregado a partir do arquivo PFX.
     * @throws Exception Se ocorrer algum erro durante o carregamento do certificado.
     */
    Certificado loadCertificado(String nomeArquivo, String senha) throws Exception {
        URI uri = Objects.requireNonNull(ThreadSafeTest.class.getClassLoader().getResource(nomeArquivo)).toURI();
        return CertificadoService.certificadoPfx(Paths.get(uri).toString(), senha);
    }

    /**
     * Carrega os dados do protocol, obtém informações adicionais do factory e grava em log.
     */
    void checkProtocolo() {
        Protocol registeredProtocol = Protocol.getProtocol("https");
        ProtocolSocketFactory factory = registeredProtocol.getSocketFactory();

        try {
            Class<?> clazz = factory.getClass();

            Field getSenhaField = clazz.getDeclaredField("senha");
            getSenhaField.setAccessible(true);
            String senha = (String) getSenhaField.get(factory);
            log.info("Senha: " + senha);

            Field getAliasField = clazz.getDeclaredField("alias");
            getAliasField.setAccessible(true);
            String aliasFactory = (String) getAliasField.get(factory);
            log.info("Alias: " + aliasFactory);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    synchronized void iniciaCertificado(String nomeArquivo, String senha) {
        try {
            CertificadoService.inicializaCertificado(loadCertificado(nomeArquivo, senha));
        } catch (Exception e) {
            e.printStackTrace();
        }
        checkProtocolo();
    }

    /**
     * Método de teste para verificar a concorrência de inicialização de certificados.
     * Este teste cria duas threads que inicializam certificados diferentes e verificam o protocolo.
     * 
     * <p>
     * Primeiro as threads são configuradas, depois inicializam juntas.
     * 
     * <p>
     * Nos logs, podemos ver que a senha e o alias do certificado são compartilhados entre as
     * threads mesmo sendo certificados diferentes.
     * 
     * <p>
     * Resultado: Vamos ver senha e alias de um certificado se repetindo no log.
     */
    @Test
    void test() {
        log.info("test1 - Iniciando teste de concorrência");

        Runnable tarefa1 = () -> {
            try {
                CertificadoService.inicializaCertificado(loadCertificado("NAO_UTILIZE.pfx", "123456"));
            } catch (Exception e) {
                e.printStackTrace();
            }
            checkProtocolo();
        };

        Runnable tarefa2 = () -> {
            try {
                CertificadoService.inicializaCertificado(loadCertificado("Emp1.pfx", "secreta"));
            } catch (Exception e) {
                e.printStackTrace();
            }
            checkProtocolo();
        };

        // Criação das threads com as tarefas definidas
        Thread thread1 = new Thread(tarefa1);
        Thread thread2 = new Thread(tarefa2);

        // Iniciação das threads
        thread1.start();
        thread2.start();

        // Aguarda o término das threads
        try {
            thread1.join();
            thread2.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }
    }

    /**
     * Método de teste para verificar a concorrência de inicialização de certificados.
     * Este teste é parecido com o test(), porém seu acesso é através da function iniciaCertificado()
     * que está marcada como Syncronized.
     *
     * Syncronized permite a execução sem concorrência, ou seja, uma thread só pode acessar o método por vez.
     * Porem é possível que tenha erros de desempenho, deadlock ou outras limitações.
     * 
     * <p>
     * Resultado: Vamos ver senha e alias de cada um dos dois certificados.
     */
    @Test
    void test2() {
        log.info("test2 - Iniciando teste de concorrência");

        Runnable tarefa1 = () -> {
            iniciaCertificado("NAO_UTILIZE.pfx", "123456");
        };

        Runnable tarefa2 = () -> {
            iniciaCertificado("Emp1.pfx", "secreta");
        };

        // Criação das threads com as tarefas definidas
        Thread thread1 = new Thread(tarefa1);
        Thread thread2 = new Thread(tarefa2);

        // Iniciação das threads
        thread1.start();
        thread2.start();

        // Aguarda o término das threads
        try {
            thread1.join();
            thread2.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }
    }

    /**
     * Método de teste para verificar a concorrência de inicialização de certificados.
     * Este teste inicializa certificados diferentes e verificam o protocolo de forma sequencial.
     * 
     * <p>
     * Resultado: Vamos ver senha e alias de cada um dos dois certificados.
     * Porém precisam ser executados de forma sequencial.
     * 
     * @throws CertificadoException se houver um erro com o certificado.
     * @throws Exception se ocorrer uma exceção inesperada.
     */
    @Test
    void test3() throws CertificadoException, Exception{
        CertificadoService.inicializaCertificado(loadCertificado("NAO_UTILIZE.pfx", "123456"));
        checkProtocolo();
        CertificadoService.inicializaCertificado(loadCertificado("Emp1.pfx", "secreta"));
        checkProtocolo();
    }
}