package com.cadprev.reader.email;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.util.StringUtils;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SpringBootApplication
public class CadprevEmailReaderApplication implements ApplicationRunner {

	static Logger log = Logger.getLogger(CadprevEmailReaderApplication.class);

	private static final String BASE = "/home/eduardorost/Downloads";
	private static final String FOLDER = BASE + "/DAIR/";
	
	public static void main(String[] args) {
		SpringApplication.run(CadprevEmailReaderApplication.class, args);
	}

	@Override
	public void run(ApplicationArguments args) throws IOException, InterruptedException {
		ArrayList<Email> emailList = (ArrayList<Email>) FileUtils.listFiles(new File(FOLDER), new String[]{"pdf"}, true)
				.stream().map(file -> readPdf((File) file))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
		//List<Email> emailList = Files.list(Paths.get(FOLDER)).map(this::findUFs).flatMap(List::stream).collect(Collectors.toList());
		generateCSV(emailList);
	}

	private String getEmailRUG(List<String> lines) {
		ArrayList<String> headersAndInfos = getSectionInformation(lines, "DADOS DA UNIDADE GESTORA", "MINISTÉRIO DA PREVIDÊNCIA SOCIAL - MPS", true);
		List<String> collect = headersAndInfos.stream().map(s -> s.split(" ")).flatMap(Arrays::stream).collect(Collectors.toList());
		String email = getInfo("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+", new ArrayList<>(collect));

		if(!StringUtils.isEmpty(email))
			return email;

		return null;
	}

	private String getEmailRepresentanteEnte(List<String> lines) {
		ArrayList<String> headersAndInfos = getSectionInformation(lines, "DADOS DO REPRESENTANTE LEGAL DO ENTE", "ENTE", false);
		try {
			return headersAndInfos.get(8).substring(10);
		} catch (Exception ex) {
			return null;
		}
	}

	private Email readPdf(File file) {
		try (PDDocument document = PDDocument.load(file)) {
			List<String> lines = getPdfLines(document);

			ArrayList<String> headersAndInfosENTE = getSectionInformation(lines, "DEMONSTRATIVO DE APLICAÇÕES E INVESTIMENTOS DOS RECURSOS - DAIR", "DADOS DO REPRESENTANTE LEGAL DO ENTE", false);
			String cidade = headersAndInfosENTE.get(0).split(":")[1];
			String uf = headersAndInfosENTE.stream().filter(s -> s.length() == 2).findFirst().orElse("");

			String emailRUG = getEmailRUG(lines);
			String emailRepresentanteEnte = getEmailRepresentanteEnte(lines);

			return new Email(uf, cidade, emailRUG, emailRepresentanteEnte);
		} catch (Exception e) {
			log.info("erro processar arquivo", e);
		}

		return null;
	}

	private List<String> getPdfLines(PDDocument document) throws IOException {
		PDFTextStripper pdfTextStripper = new PDFTextStripper();
		return Arrays.asList(pdfTextStripper.getText(document).split(pdfTextStripper.getLineSeparator()));
	}

	private void generateCSV(List<Email> emailList) throws IOException {
		String csv = "uf;cidade;emailRUG;emailRepresentantesEnte" + System.lineSeparator();
		csv += emailList.stream().map(Email::toString).collect(Collectors.joining(System.getProperty("line.separator")));

		try(BufferedWriter fw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(BASE + "/email.csv")),"UTF-8"))) {
			fw.write(csv);
		}

		//try(PrintWriter out = new PrintWriter(BASE + "/email.csv")){
			//out.println(csv);
		//}
	}

	private ArrayList<String> getSectionInformation(List<String> lines, String start, String end, boolean removeLast) {
		List<String> strings = lines.subList(lines.indexOf(start) + 1, lines.size());
		return new ArrayList<>(strings.subList(0, removeLast ? strings.indexOf(end) - 1 : strings.indexOf(end)));
	}

	private ArrayList<String> getInfos(int start, Integer end, ArrayList<String> headersAndInfos) {
		return new ArrayList<>(headersAndInfos.subList(start, end != null ? end : headersAndInfos.size()));
	}

	private String getInfo(String pattern, ArrayList<String> strings) {
		Pattern p = Pattern.compile(pattern);
		Optional<Matcher> matcherOptional = strings.stream().map(p::matcher)
				.filter(Matcher::matches)
				.findFirst();

		if (matcherOptional.isPresent() && matcherOptional.get().matches())
			return matcherOptional.get().group();

		return "";
	}

}
