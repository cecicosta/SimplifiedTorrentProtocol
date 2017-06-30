package com.bittorrent.tracker;

import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Semaphore;

import sockets.MetaData;

public class Servidor {
	
	private ServerSocket socket;

	public Servidor(){
	}
	
	public void iniciarServidor(){
	      try {
	    	  //Converte o parametro recebido para int (número da porta)    
	          int port = Integer.parseInt("12345");          
	          System.out.println("Incializando o servidor...");
	          //Iniciliza o servidor
	          socket = new ServerSocket(port);
	          System.out.println("Servidor iniciado, ouvindo a porta " + port);
	          //Aguarda conexões
	          while(true) {
	               Socket clie = socket.accept();
	               //Inicia thread do cliente
	               new ThreadServidor(clie).start();
	          }    
	      }
	      catch(Exception e) {}
	}
}



class ThreadServidor extends Thread {

	static private Semaphore semaforo = new Semaphore(1);
	private Socket cliente;

	public ThreadServidor(Socket cliente) {
		  this.cliente = cliente; 
	}

	public void run() {
		try {
			
			//ObjectInputStream para receber o nome do arquivo
			ObjectInputStream entrada = new ObjectInputStream(cliente.getInputStream());
			DataOutputStream saida  = new DataOutputStream(cliente.getOutputStream());
			
			//Recebe o nome do arquivo
			String arquivo = (String)entrada.readObject();
			
			//Buffer de leitura dos bytes do arquivo
			byte buffer[] = new byte[MetaData.TAM_SEG];
			
			semaforo.acquire();
			//Leitura do arquivo solicitado
			File file = new File(arquivo);

			RandomAccessFile accessFile = new RandomAccessFile(file, "r");
			while(cliente.isConnected()){
				
				//Recebe a informação do segmento solicitado
				String segId = null;
				try{
					segId = (String)entrada.readObject();		
				}catch(EOFException e){
					System.out.println("Não há mais segmentos a serem transferidos. Fechando a conexão.");
					cliente.close();
					break;
				}
				int numPacote = Integer.valueOf(segId);
				System.out.println(numPacote);
				//DataInputStream para processar o arquivo solicitado
				saida.flush();
				accessFile.seek(numPacote*MetaData.TAM_SEG);
				int leitura = accessFile.read(buffer);
				System.out.println(leitura);
				if(leitura != -1)
					saida.write(buffer,0,leitura);
					
				System.out.println("Cliente atendido com sucesso: " + arquivo +
					cliente.getRemoteSocketAddress().toString());
			}  
			accessFile.close();
			entrada.close();
			saida.close();
			semaforo.release();
		}catch(Exception e) {
			System.out.println("Excecao ocorrida na thread: " + e.getMessage());     
			e.printStackTrace();
			try {
				cliente.close();
			}catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} 
		}
	}
}
