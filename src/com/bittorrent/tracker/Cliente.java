package com.bittorrent.tracker;

import java.beans.PersistenceDelegate;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

import sockets.MetaData;

public class Cliente {

	public  static HashMap<String, Semaphore> tempFilesAcess = new HashMap<String, Semaphore>();

	private Semaphore semaforoSegmentos = new Semaphore(1);
	private Semaphore semaforoFontes = new Semaphore(1);
	private MetaData metadados;
	private ArrayList<Integer> carregados = new ArrayList<Integer>();
	private ArrayList<Integer> pendentes = new ArrayList<Integer>();
	private ArrayList<String> peers;
	public Cliente(MetaData metadata){
		this.metadados = metadata;
		tempFilesAcess.put(getTempFileName(), new Semaphore(1));
		String[] ips = (String[])metadata.properties.getProperty(MetaData.ANNOUNCE).split(" ");
		peers = new ArrayList<String>(Arrays.asList(ips));
		checaSegmentosCarregados(metadata);
		System.out.println("Cliente: Cliente iniciado ");
	}

	private void checaSegmentosCarregados(MetaData metadata)  {
		File file = null;
		file = new File(getTempFileName());
		//Inicializa segmentos pendentes
		for(int i=0; i< metadata.getQuantSegmentos(); i++){
			pendentes.add(i);
		}
		//Lê arquivo temporário para determinar quais segmentos já foram carregados
		RandomAccessFile reader = null;
		try {
			tempFilesAcess.get(getTempFileName()).acquire();
			reader = new RandomAccessFile(file, "r");
		} catch (FileNotFoundException | InterruptedException e1) {
			
			tempFilesAcess.get(getTempFileName()).release();
			//Cria novo arquivo temporário se o arquivo não for encontrado
			criarArquivoTemporario(metadata);
			return;	
		}
		byte[] buffer = new byte[MetaData.TAM_SEG];

		for(int i=0; i< metadata.getQuantSegmentos(); i++){
			try {
				reader.seek(i*MetaData.TAM_SEG);
				reader.read(buffer);
			
				String key = String.valueOf(i);
				String hash = "";
				try {
					hash = metadata.calculaHash(buffer);
				} catch (NoSuchAlgorithmException e) {
					e.printStackTrace();
				}
				String hashOriginal = metadata.properties.getProperty(key, "not found");
				
				if(hash.compareTo(hashOriginal) == 0){
					carregados.add(i);	
					pendentes.remove((Integer)i);
				}
				
			} catch (IOException e) {
				e.printStackTrace();
			}catch(NumberFormatException e){
				
			}
		}
		tempFilesAcess.get(getTempFileName()).release();
		try {
			reader.close();
		} catch (IOException e) {
		}
		if(carregados.size() == metadata.getQuantSegmentos()){
			System.out.println("Loaded: " + carregados.size() + "segmentos");
			System.out.println("Arquivo completo... Montando...");
		}
	}

	private String getTempFileName() {
		return metadados.properties.getProperty(MetaData.NAME) + ".temp";
	}

	private void criarArquivoTemporario(MetaData metadata) {
		try {
			tempFilesAcess.get(getTempFileName()).acquire();
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		File temp = new File(getTempFileName());
		RandomAccessFile writter = null;
		try {
			writter = new RandomAccessFile(temp, "rw");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		try {
			writter.setLength(Integer.valueOf(metadata.properties.getProperty(MetaData.LENGTH)));
		} catch (NumberFormatException | IOException e) {
			e.printStackTrace();
		}
		try {
			writter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		tempFilesAcess.get(getTempFileName()).release();
		System.out.println("Cliente: Arquivo temporário criado: " + getTempFileName());
	}
	
	private void salvarSegmento(byte[] segmento, String key) throws IOException {
		File file = new File(metadados.properties.getProperty(MetaData.NAME) + ".temp");
		RandomAccessFile writter = new RandomAccessFile(file, "rw");
		writter.seek(Integer.valueOf(key)*MetaData.TAM_SEG);
		writter.write(segmento);
		writter.close();
	}
	
	public void criaConexoes(){
		System.out.println("Cliente: Fontes encontradas: " + peers.size());
		for(int i=0; i<peers.size(); i++){
			try {
				if(!pendentes.isEmpty()){
					System.out.println("Cliente: Conectando: " + peers.get(i));
					Socket socket = new Socket(peers.get(i), 12345);
					System.out.println("Cliente: Conectado! ");
					Thread t = new Thread(new Runnable(){
						
						@Override
						public void run() {
							requisicao(socket);
						}
						
					});
					t.start();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void conexoesDinamicas(ArrayList<String> fontes){
		try {
			semaforoFontes.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		for(int i=0; i<fontes.size(); i++){
			if(!peers.contains(fontes.get(i))){
				peers.add(fontes.get(i));
			}else{
				continue;
			}
			try {
				if(!pendentes.isEmpty()){
					System.out.println("Server addr: " + fontes.get(i));
					Socket socket = new Socket(fontes.get(i).substring(1), 12345);
					Thread t = new Thread(new Runnable(){
						@Override
						public void run() {
							requisicao(socket);
						}
					});
					t.start();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		semaforoFontes.release();
	}

	public void retornaSegmentos(ArrayList<Integer> ret){
		try {
			semaforoSegmentos.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		pendentes.addAll(ret);
		semaforoSegmentos.release();
	}
	public ArrayList<Integer> selecionarSegmentos(ArrayList<Integer> disponiveis){
		ArrayList<Integer> sel = new ArrayList<Integer>();

		ArrayList<Integer> intersec = new ArrayList<Integer>(pendentes);
		intersec.retainAll(disponiveis);
		for(int i=0; i < 4; i++){
			try {
				semaforoSegmentos.acquire();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if(intersec.isEmpty()){
				System.out.println("Não há mais segmentos pendentes.");
				semaforoSegmentos.release();
				break;
			}
			sel.add((Integer)intersec.get(0));
			pendentes.remove((Integer)intersec.get(0));
			intersec.remove(0);
			semaforoSegmentos.release();
		}
		return sel;
	}
	
	public void requisicao(Socket socket){
		try {
			System.out.println("Cliente: Iniciando requisições: ");
			byte[] segmentoBytes = new byte[MetaData.TAM_SEG];
			ObjectOutputStream saida = new ObjectOutputStream(socket.getOutputStream());
			DataInputStream entrada = new DataInputStream(socket.getInputStream());
			ObjectInputStream object = new ObjectInputStream(socket.getInputStream());
			
			//Envia metadados para o servidor
			saida.writeObject(metadados);
			System.out.println("Cliente: Metadados enviados: " + metadados.nome);
			
			//Recebe novas fontes do servidor
			ArrayList<String> fontes = (ArrayList<String>)object.readObject();
			Thread t = new Thread(new Runnable(){
				@Override
				public void run() {
					conexoesDinamicas(fontes);
				}
			});
			t.start();
			System.out.println("Cliente: Fontes recebidas: " + fontes.size());
			

			ArrayList<Integer> segmentos = new ArrayList<>();
		
			while(true){
				
				//Obter lista de segmentos que o servidor possui
				ArrayList<Integer> disponiveis = (ArrayList<Integer>)object.readObject();
				if(segmentos.isEmpty()){
					System.out.println("Cliente: Segmentos disponiveis na fonte "+ 
					socket.getRemoteSocketAddress().toString() + ": " + disponiveis.size());
					segmentos = selecionarSegmentos(disponiveis);
					System.out.println("Cliente: Segmentos selecionados: "+ segmentos.size());
				}
				//Se todos os segmentos foram recebidos com sucesso, solicita mais registros de segmentos
				if(segmentos.isEmpty()){
					System.out.println("Servidor não possui segmentos disponíveis: " + metadados.nome +
							socket.getRemoteSocketAddress().toString());
					break;
				}
				
				int seg = segmentos.remove(0);			
				//Envia segmento que será recebido
				saida.writeObject(String.valueOf(seg));
				

				
				//Recebe segmento
				entrada.read(segmentoBytes);
				
				//Faz a validação do segmento
				String key = String.valueOf(seg);
				String hash = metadados.calculaHash(segmentoBytes);
				String hashOriginal = metadados.properties.getProperty(key, "not found");
				
				System.out.println("Client: segmento id: " + seg);
				System.out.println("Client: Received: " + hash);
				System.out.println("Client: Original: " + hashOriginal);
				
				//Faz a checagem se a hash do segmento recebido condiz com a do segmento esperado
				if(hashOriginal.compareTo(hash) == 0){
					tempFilesAcess.get(getTempFileName()).acquire();
					try{
						salvarSegmento(segmentoBytes, key);
					}catch(Exception e){
						segmentos.add(seg);
						retornaSegmentos(segmentos);
						tempFilesAcess.get(getTempFileName()).release();
						break;
					}
					tempFilesAcess.get(getTempFileName()).release();
					System.out.println("Segmento recebido com sucesso: " + metadados.nome +
							socket.getRemoteSocketAddress().toString());
				}else{
					System.out.println("Segmento recebido não condiz com o esperado. Fechando a conexão com o par..."); 
					//Erro no recebimento: Devolve o registro de segmentos pendentes e segue para encerrar a conexão
					segmentos.add(seg);
					retornaSegmentos(segmentos);
					tempFilesAcess.get(getTempFileName()).release();
					break;
				}
			}
			
			entrada.close();
			saida.close();
			socket.close();
			object.close();
		}
	
		catch(Exception e) {
			e.printStackTrace();
	    }
	}             
	
}
