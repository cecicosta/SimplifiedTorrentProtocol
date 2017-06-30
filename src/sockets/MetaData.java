//Luis,Rubia,Raphael
package sockets;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Properties;

public class MetaData {
	
	public Properties properties;
	public static final String QUANTIDADE_SEG = "QuantidadeSeg";
	public static final String TAMANHO_SEG = "TamanhoCadaSeg";
	public static final String LENGTH = "lengh"; //Tamanho do arquivo em bytes
	public static final String NAME = "name";
	public static final String ANNOUNCE = "announce";
	
	private long fileSize;
	private int qntSegmentos;
	public static final int TAM_SEG = 1024;
	
	public String nome;
	private File file, torrentFile;
	private MessageDigest calcHash;
	
	
	
	public MetaData() throws IOException {
		properties = new Properties();
		try {
			calcHash = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void criaTorrent(String arquivo, String peers) throws NoSuchAlgorithmException, IOException{
		
		this.nome = arquivo;
		this.file = new File(arquivo);
		
		this.fileSize = file.length();
		this.qntSegmentos = (int) Math.ceil( ((double) fileSize)/TAM_SEG );
		
		this.torrentFile = new File(file.getAbsolutePath()+".trr"); //abre um filedescriptor
		
		//Checar se deseja substituir arquivo existente
		//if(this.torrentFile.exists())
		
		RandomAccessFile fileReader = new RandomAccessFile(this.file, "r");
		
		properties.setProperty(NAME, this.nome);
		properties.setProperty(ANNOUNCE, peers);
		properties.setProperty(LENGTH, String.valueOf(this.fileSize) );
		properties.setProperty(TAMANHO_SEG, String.valueOf(TAM_SEG));
		properties.setProperty(QUANTIDADE_SEG, String.valueOf(this.qntSegmentos));
		
		calculaHashSegmentos(fileReader);
		
		BufferedOutputStream torrentWriter = new BufferedOutputStream(new FileOutputStream(this.torrentFile));
		
		//grava os metadados no arquivo .torrent
		properties.store(torrentWriter, null); //null seriam os comentários se houvesse
		torrentWriter.close();
	}
	
	public void carregaTorrent(String torrentPath) throws IOException, NoSuchAlgorithmException {
		this.torrentFile = new File(torrentPath);
		
		BufferedInputStream torrentReader = new BufferedInputStream(new FileInputStream(this.torrentFile));

			this.properties = new Properties();
			this.properties.load(torrentReader);
		
		torrentReader.close();
		
		this.file = new File((String) this.properties.get(NAME));
		
		//verificaHashSegmentos(fileReader);
		//SUGESTAO: se o arquivo existir, percorra cada peca, reclacule o hash e compare com o hash do properties.get(i), se bater vc já tem a peça 
	}

	private void calculaHashSegmentos(RandomAccessFile fileReader) throws NoSuchAlgorithmException, IOException {
		byte[] segmentoBytes =  new byte[TAM_SEG];
		
		for(int i = 0; i < this.qntSegmentos; i++){
			//carrega o segmento
			fileReader.seek(i*TAM_SEG);
			fileReader.read(segmentoBytes);
			
			//calcula hash pro segmento
			String hash = calculaHash(segmentoBytes);
			
			//salva valor hash no arquivo metadados
			properties.setProperty(String.valueOf(i), hash);
			
			//zera o hash pra estar limpo pra proximo segmento
			calcHash.reset();
			
			System.out.println("[DEBUG] hash do segmento [" + (i+1) + "] = " + hash);
		}
	}
	
	public String calculaHash(byte[] segmento) throws NoSuchAlgorithmException, IOException {		
		byte[] hashSegmento;
		
		
		//calcula hash pro segmento
		calcHash.update(segmento);
		hashSegmento = calcHash.digest();
		
		//zera o hash pra estar limpo pra proximo segmento
		calcHash.reset();		
	
		return Arrays.toString(hashSegmento);
	}
	
	public int getQuantSegmentos(){
		return Integer.valueOf(properties.getProperty(MetaData.QUANTIDADE_SEG));
	}
}
