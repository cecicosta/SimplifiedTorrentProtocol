//Luis,Rubia,Raphael
package sockets;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;

import com.bittorrent.tracker.Cliente;
import com.bittorrent.tracker.Servidor;

public class Main {
	
	//Lista com ip:porta dos computadores da rede que possuem o arquivo e estão ouvindo a conexão
	//separados por ' '
	static String peers = "";
	static MetaData torrentData = null;
	private static Scanner in = new Scanner(System.in);

	
	
	public static void main(String[] args) throws NoSuchAlgorithmException {
		
		System.out.println("Escolha uma opção: \n"
				+ "1) Criar um arquivo .trr\n"
				+ "2) Carregar um arquivo .trr para download\n");
		String entrada = in.nextLine();
		switch(Integer.parseInt(entrada)){
		case 1:
			try {
				torrentData = criaTorrentNovo();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			break;
		case 2:
			try {
				torrentData = carregaTorrentExistente();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
			
			Thread threadServer = new Thread(new Runnable(){
				@Override
				public void run() {
					Servidor servidor = new Servidor();
					servidor.iniciarServidor();
				}
				
			});
			threadServer.start();
			Thread threadClient = new Thread(new Runnable(){
				@Override
				public void run() {
					Cliente cliente = new Cliente(torrentData);
					cliente.criaConexoes();
				}
				
			});
			threadClient.start();
		}
	}

	private static MetaData criaTorrentNovo() throws NoSuchAlgorithmException, IOException {
		MetaData torrentData = new MetaData();
		
		System.out.println("Digite o nome do arquivo para criar um .trr: ");
		String entrada = in.nextLine();
		System.out.println("Digite a lista de pares <ip> : \n"
				+ "e.x.:192.198.1.2 192.168.1.3");
		String pares = in.nextLine();
		torrentData.criaTorrent(entrada, pares);
		
		return torrentData;
	}
	
	private static MetaData carregaTorrentExistente() throws NoSuchAlgorithmException, IOException {
		MetaData torrentData = new MetaData();
		
		System.out.println("Digite o nome do .trr para baixar o arquivo: ");
		String caminhoTorrent = in.nextLine();
		torrentData.carregaTorrent(caminhoTorrent);
		
		return torrentData;
	}
}
