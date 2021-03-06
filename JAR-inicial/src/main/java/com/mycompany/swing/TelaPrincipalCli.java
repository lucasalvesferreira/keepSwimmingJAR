package com.mycompany.swing;

import com.github.britooo.looca.api.group.discos.Disco;
import com.github.britooo.looca.api.group.discos.DiscosGroup;
import com.github.britooo.looca.api.group.memoria.Memoria;
import com.github.britooo.looca.api.group.processador.Processador;
import com.github.britooo.looca.api.group.processos.Processo;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

public class TelaPrincipalCli {

    private Funcionario funcionario;

    // classe de de conexão com o banco
    private Connection connection;
    
    // conexão docker
    private Connection conLocal;
    
    //template docker
    private JdbcTemplate templateLocal;

    //conexão com o banco
    private JdbcTemplate template;

    // looca
    private com.github.britooo.looca.api.core.Looca looca;

    public TelaPrincipalCli(Funcionario func) {

        this.funcionario = func;
        
        //conexão AZURE
        this.connection = new Connection();
        this.template = new JdbcTemplate(connection.getDatasource());
        
        //conexão DOCKER
        Boolean mysql = true;
        this.conLocal = new Connection(mysql);
        this.templateLocal = new JdbcTemplate(this.conLocal.getDatasource());
        
        this.looca = new com.github.britooo.looca.api.core.Looca();


        inicializacao();

    }

    public TelaPrincipalCli() {
    }

    public Funcionario getFuncionario() {
        return funcionario;
    }

    public void setFuncionario(Funcionario funcionario) {
        this.funcionario = funcionario;
    }

 private void inicializacao() {
        Timer timer = new Timer();
        Integer delay = 1000;
        Integer interval = 5000;

        Integer idDaMaquina = buscarIdDaMaquina();

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                System.out.println("Atualizando dados...");

                buscarProcessos(idDaMaquina);
                buscarHistorico(idDaMaquina);

            }
        }, delay, interval);

        buscarDadosHardware(idDaMaquina);
    }

    private Integer buscarIdDaMaquina() {
        /////////////////////  Pegando o id da Maquina    ///////////////////////
        List<maquina> idMaquina = template.query("select idMaquina from [dbo].[Maquina] \n"
                + "JOIN [dbo].[FUNCIONARIO] on fkUsuario = idFuncionario \n"
                + "WHERE idFuncionario = " + funcionario.getIdFuncionario(),
                new BeanPropertyRowMapper(maquina.class));

        System.out.println("pegando o ID MAQUINA" + idMaquina.toString());

        return idMaquina.get(0).getIdMaquina();
    }

    private void buscarProcessos(Integer idDaMaquina) {
        System.out.println("Buscando processos...");

        List<Processo> processos = looca.getGrupoDeProcessos().getProcessos();
        List<Processo> processosFiltrados = new ArrayList<>();
        Date dataHoraProcesso = new Date();

        for (Processo processo : processos) {
            if (processo.getUsoCpu() > 1 || processo.getUsoMemoria() > 1) {
                processosFiltrados.add(processo);
            }
        }

        System.out.println(String.format("Salvando %d processos", processosFiltrados.size()));

        Integer totalProcessos = looca.getGrupoDeProcessos().getTotalProcessos();
        Integer threads = looca.getGrupoDeProcessos().getTotalThreads();
        
        System.out.println("Inserindo no docker");
        
        //Insert Mysql
        String inserirDadosProcessosLocal = "Insert into Processos VALUES "
              + "(null,?,?,?,?,?,?,?,?,?,?);";
        
        System.out.println("Inserindo na AZURE");
        
        //Insert AZURE
        String inserirDadosProcessos = "Insert into Processos VALUES "
                + "(?,?,?,?,?,?,?,?,?,?)";
        
        //INSERT LOCAL(DOCKER)
        templateLocal.batchUpdate(inserirDadosProcessosLocal, new BatchPreparedStatementSetter() {

            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Integer pid = processosFiltrados.get(i).getPid();
                String nome = processosFiltrados.get(i).getNome();
                Double usoCpu = processosFiltrados.get(i).getUsoCpu();
                Double usoMemoria = processosFiltrados.get(i).getUsoMemoria();
                Long bytesUtilizados = processosFiltrados.get(i).getBytesUtilizados();
                Long memVirtualUtilizada = processosFiltrados.get(i).getMemoriaVirtualUtilizada();
                
                System.out.println("Inserindo processo local: " + dataHoraProcesso);
                
        
                
                ps.setInt(1, idDaMaquina);
                ps.setInt(2, pid);
                ps.setString(3, nome);
                ps.setDouble(4, usoCpu);
                ps.setDouble(5, usoMemoria);
                ps.setLong(6, bytesUtilizados);
                ps.setLong(7, memVirtualUtilizada);
                ps.setInt(8, totalProcessos);
                ps.setInt(9, threads);
                ps.setTimestamp(10, new Timestamp(dataHoraProcesso.getTime()));
            }

            @Override
            public int getBatchSize() {
                return processosFiltrados.size();
            }

        });

        // INSERT AZURE
        template.batchUpdate(inserirDadosProcessos, new BatchPreparedStatementSetter() {

            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Integer pid = processosFiltrados.get(i).getPid();
                String nome = processosFiltrados.get(i).getNome();
                Double usoCpu = processosFiltrados.get(i).getUsoCpu();
                Double usoMemoria = processosFiltrados.get(i).getUsoMemoria();
                Long bytesUtilizados = processosFiltrados.get(i).getBytesUtilizados();
                Long memVirtualUtilizada = processosFiltrados.get(i).getMemoriaVirtualUtilizada();
                
                System.out.println("Inserindo processo: " + pid + " " + nome + " CPU: " + usoCpu + " Memória: " + usoMemoria + " Datahora: " + dataHoraProcesso);               
                
                ps.setInt(1, idDaMaquina);
                ps.setInt(2, pid);
                ps.setString(3, nome);
                ps.setDouble(4, usoCpu);
                ps.setDouble(5, usoMemoria);
                ps.setLong(6, bytesUtilizados);
                ps.setLong(7, memVirtualUtilizada);
                ps.setInt(8, totalProcessos);
                ps.setInt(9, threads);
                ps.setTimestamp(10, new Timestamp(dataHoraProcesso.getTime()));
            }

            @Override
            public int getBatchSize() {
                return processosFiltrados.size();
            }

        });
    }

    private void buscarDadosHardware(Integer idDaMaquina) {
        System.out.println("Buscando dados de hardware...");

        DiscosGroup disco = looca.getGrupoDeDiscos();
        Memoria memoria = looca.getMemoria();
        Processador processador = looca.getProcessador();

        List<Disco> listaDeDisco = disco.getDiscos();

        for (int i = 0; i < listaDeDisco.size(); i++) {

            String nomeDisco = disco.getDiscos().get(i).getNome();
            Long tamanhoDisco = disco.getDiscos().get(i).getTamanho();
            String modeloDisco = disco.getDiscos().get(i).getModelo();
            Integer qtdDiscos = disco.getQuantidadeDeDiscos();
            Long memoriaTotal = memoria.getTotal();
            String processadorNome = processador.getNome();

            System.out.println("Inserindo no docker");
            
                 //Para Mysql local
        String inserirDadosHardwareLocal = "Insert into ComponentesHardware VALUES" 
                  + "(null,?,?,?,?,?,?,?);";
        templateLocal.update(inserirDadosHardwareLocal,
                            idDaMaquina,
                            nomeDisco,
                            tamanhoDisco,
                            modeloDisco,
                            qtdDiscos, 
                            memoriaTotal,
                            processadorNome);
        
            System.out.println("Inserindo na AZURE");
        
            //Para AZURE
            String inserirDadosHardware = "Insert into ComponentesHardware VALUES"
                    + "(?,?,?,?,?,?,?);";

            template.update(inserirDadosHardware,
                    idDaMaquina,
                    nomeDisco,
                    tamanhoDisco,
                    modeloDisco,
                    qtdDiscos,
                    memoriaTotal,
                    processadorNome);
        }
    }

    private void buscarHistorico(Integer idDaMaquina) {
        System.out.println("Buscando histórico...");

        Date data = new Date();
         DiscosGroup disco = looca.getGrupoDeDiscos();
        List<Disco> listaDeDisco = disco.getDiscos();
        
        for(Integer i = 0; i < listaDeDisco.size(); i++)
        {
            System.out.println("BYTES DE LEITURA: " +  listaDeDisco.get(i).getBytesDeLeitura());
        }
        

        Memoria memoria = looca.getMemoria();
        Processador processador = looca.getProcessador();

        String tempoInicializado = looca.getSistema().getInicializado().toString();
        String tempoDeAtividade = looca.getSistema().getTempoDeAtividade().toString();
        String temperaturaAtual = looca.getTemperatura().toString();
        Long memoriaEmUso = memoria.getEmUso();
        Long memoriaDisponível = memoria.getDisponivel();
        Double processadorUso = processador.getUso();
        
        System.out.println("Inserindo no docker");
        
         //MySQL local         
            String inserirHistoricoLocal = "Insert into Historico VALUES "
                + "(null,?,?,?,?,?,?,?,?);";
        templateLocal.update(inserirHistoricoLocal,idDaMaquina,data,tempoInicializado,tempoDeAtividade,
               temperaturaAtual,memoriaEmUso,memoriaDisponível,processadorUso);
        
        System.out.println("inserindo na AZURE");

        //AZURE
        String inserirHistorico = "Insert into Historico VALUES "
                + "(?,getdate(),?,?,?,?,?,?);";

        template.update(inserirHistorico, idDaMaquina, tempoInicializado, tempoDeAtividade,
                temperaturaAtual, memoriaEmUso, memoriaDisponível, processadorUso);

        System.out.println("Data " + data);
        System.out.println("Tempo inicializado " + tempoInicializado);
        System.out.println("Tempo de atividade " + tempoDeAtividade);
        System.out.println("Temperatura atual " + temperaturaAtual);
        System.out.println("Memoria em uso " + memoriaEmUso);
        System.out.println("Memoria disponível " + memoriaDisponível);
        System.out.println("Uso do processador " + processadorUso);
        
       
        
    }
    
}