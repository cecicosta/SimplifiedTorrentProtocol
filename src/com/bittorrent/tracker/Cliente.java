package com.bittorrent.tracker;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Semaphore;

import sockets.MetaData;

public class Cliente {

	private Semaphore semaforoSegmentos = new Semaphore(1);
	private Semaphore semaforoTempWrite = new Semaphore(1);
	private MetaData metadata;
	private ArrayList<Integer> carregados = new ArrayList<Integer>();
	private ArrayList<Integer> pendentes;
	private ArrayList<String> peers;
	public Cliente(MetaData metadata){
		this.metadata = metadata;
		pendentes = new ArrayList<Integer>();
		String[] ips = (String[])metadata.properties.getProperty(MetaData.ANNOUNCE).split(" ");
		peers = new ArrayList<String>(Arrays.asList(ips));
		checaSegmentosCarregados(metadata);
	}

	private void checaSegmentosCarregados(MetaData metadata) {
		FileReader file = null;
		try {
			file = new FileReader(getTempFileName());
		} catch (FileNotFoundException e) {
			criarArquivoTemporario(metadata);
			System.out.println("Criando arquivo temporário.");
			return;
		}
		
		for(int i=0; i<metadata.getQuantSegmentos(); i++)
			pendentes.add(i);
		//Lê arquivo temporário para determinar quais segmentos já foram carregados
		BufferedReader reader = new BufferedReader(file);
		String line = "";
		do{
			try {
				line = reader.readLine();
				if(line == null || line == "")
					break;
				else{
					int key = Integer.parseInt(line.split("=")[0]);
					if(!carregados.contains(key)){
						carregados.add(key);	
						pendentes.remove(key);
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}catch(NumberFormatException e){
				
			}
		}while(line != null);
		try {
			reader.close();
			file.close();
		} catch (IOException e) {
		}
		if(carregados.size() == metadata.getQuantSegmentos()){
			System.out.println("Loaded: " + carregados.size() + "segmentos");
			System.out.println("Arquivo completo... Montando...");
		}
	}

	private String getTempFileName() {
		return metadata.properties.getProperty(MetaData.NAME) + ".temp";
	}

	private void criarArquivoTemporario(MetaData metadata) {
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
	}
	
	private void salvarSegmento(byte[] segmento, String key) throws IOException {
		try {
			semaforoTempWrite.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		File file = new File(metadata.properties.getProperty(MetaData.NAME) + ".temp");
		RandomAccessFile writter = new RandomAccessFile(file, "rw");
		writter.seek(Integer.valueOf(key)*MetaData.TAM_SEG);
		writter.write(segmento);
		writter.close();
		semaforoTempWrite.release();
	}
	
	public void criaConexoes(){
		for(int i=0; i<peers.size(); i++){
			try {
				if(!pendentes.isEmpty()){
					Socket socket = new Socket(peers.get(i), 12345);
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

	public void retornaSegmentos(ArrayList<Integer> ret){
		try {
			semaforoSegmentos.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		pendentes.addAll(ret);
		semaforoSegmentos.release();
	}
	public ArrayList<Integer> selecionarSegmentos(){
		ArrayList<Integer> sel = new ArrayList<Integer>();
		for(int i=0; i < 4; i++){
			try {
				semaforoSegmentos.acquire();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if(pendentes.isEmpty()){
				semaforoSegmentos.release();
				break;
			}
			sel.add(pendentes.remove(0));
			semaforoSegmentos.release();
		}
		return sel;
	}
	
	public void requisicao(Socket socket){
		try {
			//Cria o Socket para buscar o arquivo no servidor 
			
			byte[] segmentoBytes = new byte[MetaData.TAM_SEG];
			ObjectOutputStream saida = new ObjectOutputStream(socket.getOutputStream());
			DataInputStream entrada = new DataInputStream(socket.getInputStream());
			
			saida.writeObject(metadata.properties.getProperty(MetaData.NAME));
			
			ArrayList<Integer> segmentos = selecionarSegmentos();
			
			while(!segmentos.isEmpty()){
				int seg = segmentos.remove(0);					
				saida.writeObject(String.valueOf(seg));
				entrada.read(segmentoBytes);
				
				String key = String.valueOf(seg);

				String hash = metadata.calculaHash(segmentoBytes);
				String hashOriginal = metadata.properties.getProperty(key, "not found");
				System.out.println("Received: " + hash);
				System.out.println("Original: " + hashOriginal);
				
				//Faz a checagem se a hash do segmento recebido condiz com a do segmento esperado
				if(hashOriginal.compareTo(hash) == 0){
					salvarSegmento(segmentoBytes, key);
				}else{
					System.out.println("Segmento recebido não condiz com o esperado. Fechando a conexão com o par..."); 
					//Erro no recebimento: Devolve o registro de segmentos pendentes e segue para encerrar a conexão
					segmentos.add(seg);
					retornaSegmentos(segmentos);
					break;
				}
				//Se todos os segmentos foram recebidos com sucesso, solicita mais registros de segmentos
				if(segmentos.isEmpty())
					segmentos = selecionarSegmentos();
			}
			
			entrada.close();
			saida.close();
			socket.close();
		}
	
		catch(Exception e) {
			e.printStackTrace();
	    }
	}             
	
}
