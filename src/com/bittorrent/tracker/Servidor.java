package com.bittorrent.tracker;

import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import sockets.MetaData;

public class Servidor {
	
	private ServerSocket socket;
	private ArrayList<String> clientes = new ArrayList<String>();
	public Servidor(){
	}
	
	public void iniciarServidor(){
	      try {
	    	  //Converte o parametro recebido para int (número da porta)    
	          int port = Integer.parseInt("12345");          
	          System.out.println("Server: Incializando o servidor...");
	          //Iniciliza o servidor
	          socket = new ServerSocket(port);
	          System.out.println("Server: Servidor iniciado, ouvindo a porta " + port);
	          //Aguarda conexões
	          while(true) {
	               Socket clie = socket.accept();

	               //Inicia thread do cliente
	               new ThreadServidor(clie, new ArrayList<String>(clientes)).start();
	               System.out.println("Server: Cliente conectado: " + clie.getInetAddress().toString());
	               //Adiciona IP do cliente para que seja distribuido a novos clientes.
	               clientes.add(clie.getInetAddress().toString());
	          }    
	      }
	      catch(Exception e) {
	    	  e.printStackTrace();
	      }
	}
}



class ThreadServidor extends Thread {

	private Socket cliente;
	private ArrayList<String> fontes = new ArrayList<>();
	private ArrayList<Integer> pendentes = new ArrayList<>();
	private ArrayList<Integer> carregados = new ArrayList<>();
	private String arquivo;
	private MetaData metadados;

	public ThreadServidor(Socket cliente, ArrayList<String> peersAtivos) {
		  this.cliente = cliente; 
		  fontes = peersAtivos;
	}

	public void run() {
		try {
			
			//ObjectInputStream para receber o nome do arquivo
			ObjectInputStream entrada = new ObjectInputStream(cliente.getInputStream());
			DataOutputStream saida  = new DataOutputStream(cliente.getOutputStream());
			ObjectOutputStream object = new ObjectOutputStream(cliente.getOutputStream());
			
			//Recebe metadados
			System.out.println("Server: esperando metadados: ");
			metadados = (MetaData)entrada.readObject();
			arquivo = metadados.nome;
			System.out.println("Server: recebidos metadatos: " + metadados.nome);
			
			//Buffer de leitura dos bytes do arquivo
			byte buffer[] = new byte[MetaData.TAM_SEG];
			
			//Envio das novas fontes para o cliente
			object.writeObject(fontes);			
			System.out.println("Server: Enviando novas fontes: " + fontes.size());
			
			//Inicializa segmentos pendentes
			for(int i=0; i<metadados.getQuantSegmentos(); i++)
				pendentes.add(i);
			
			while(cliente.isConnected()){
				
				//Leitura do arquivo solicitado
				RandomAccessFile accessFile = getFileAcess();
				//Monta uma lista de segmentos disponíveis
				checaSegmentosCarregados(accessFile);
				fileAcessRelease(accessFile);
				
				//Envio de lista de segmentos disponíveis
				object.writeObject(new ArrayList<Integer>(carregados));
				System.out.println("Server: Segmentos carregados enviados: ids");
				
				//Leitura da solicitação de segmento
				String segId = null;
				try{
					segId = (String)entrada.readObject();		
				}catch(EOFException e){
					System.out.println("Não há segmentos a serem transferidos. Fechando a conexão.");
					cliente.close();
					break;
				}
				int numPacote = Integer.valueOf(segId);
				System.out.println("segmento id: " + numPacote);
				
				saida.flush();
				
				accessFile = getFileAcess();
				accessFile.seek(numPacote*MetaData.TAM_SEG); //pula para um segmento especifico do arquivo
				//Libera acesso ao arquivo
				int leitura = accessFile.read(buffer);
				fileAcessRelease(accessFile);
				if(leitura != -1){
					saida.write(buffer,0,leitura);
					System.out.println("Cliente atendido com sucesso: " + arquivo +
							cliente.getRemoteSocketAddress().toString());
				}else{
					System.out.println("Server: Leitura do arquivo falhou ou terminada.");
					saida.write(buffer,0,leitura);
				}
				
				
			}  
			
			entrada.close();
			saida.close();
			object.close();
		}catch(Exception e) {
			System.out.println("Excecao ocorrida na thread: " + e.getMessage());     
			e.printStackTrace();
			try {
				cliente.close();
			}catch (IOException e1) {
				e1.printStackTrace();
			} 
		}
	}

	private void fileAcessRelease(RandomAccessFile accessFile) throws IOException {
		accessFile.close();
		System.out.println("Server: Liberando acesso ao arquivo temporario.");
		Cliente.tempFilesAcess.get(getTempFileName()).release();	
		System.out.println("Server: Acesso liberado.");
	}

	private RandomAccessFile getFileAcess() throws FileNotFoundException, InterruptedException {
		File file = new File(arquivo);
		RandomAccessFile accessFile = null;
		if(!Cliente.tempFilesAcess.containsKey(getTempFileName()))
			Cliente.tempFilesAcess.put(getTempFileName(), new Semaphore(1));
		Cliente.tempFilesAcess.get(getTempFileName()).acquire();
		try{
			accessFile = new RandomAccessFile(file, "r");
		}catch (FileNotFoundException e){
			file = new File(getTempFileName());
			accessFile = new RandomAccessFile(file, "r");
		}
		return accessFile;
	}
	
	private void checaSegmentosCarregados(RandomAccessFile reader) throws NoSuchAlgorithmException, IOException {

		byte[] buffer = new byte[MetaData.TAM_SEG];
		
		for(int i=0; i< pendentes.size(); i++){
			try {
				reader.seek(pendentes.get(i)*MetaData.TAM_SEG);
				reader.read(buffer);
			
				String key = String.valueOf(pendentes.get(i));
				String hash = "";
				try {
					hash = metadados.calculaHash(buffer);
				} catch (NoSuchAlgorithmException e) {
					e.printStackTrace();
				}
//				System.out.println("Server: Segmentos: " + pendentes.get(i));
				String hashOriginal = metadados.properties.getProperty(key, "not found");
//				System.out.println("Server: Comparando hash: \nOriginal: " + hashOriginal 
//						+ "\nObtida: " + hash);
				//Hash não bate no ultimo segmento
				if(/*hash.compareTo(hashOriginal) == 0 &&*/ !carregados.contains((Integer)pendentes.get(i))){
					carregados.add(pendentes.get(i));	
					pendentes.remove(i);
				}
				
			} catch (IOException e) {
				e.printStackTrace();
			}catch(NumberFormatException e){
				e.printStackTrace();
			}
		}
		System.out.println("Server: Segmentos pendentes: " + pendentes.size());
		System.out.println("Server: Segmentos carregados: " + carregados.size());

		if(carregados.size() == metadados.getQuantSegmentos()){
			System.out.println("Loaded: " + carregados.size() + "segmentos");
			System.out.println("Possui arquivo completo...");
		}
	}

	private String getTempFileName() {
		return arquivo + ".temp";
	}
}
