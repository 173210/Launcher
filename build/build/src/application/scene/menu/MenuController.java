package application.scene.menu;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.StringJoiner;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Duration;
import utility.DataUtil;
import utility.ErrorUtil;
import utility.PlatformUtil;
import utility.StringUtil;
import utility.XMLUtil;

public class MenuController implements Initializable {

	/** 作品名 */
	@FXML
	private Text workName;

	/** 製作者名 */
	@FXML
	private Text creatorName;

	/** 作品の説明 */
	@FXML
	private Text descriptionText;

	/** 開くボタン */
	@FXML
	private Button defButton;

	/** 画像を読み込むビュー */
	@FXML
	private ImageView imageView;

	/** 作品リスト */
	@FXML
	private ListView<String> listView;

	private Element root;

	private HashMap<Integer, HashMap<String, String>> dataMap;

	private int pivot = 0;

	private int size = 0;

	private ObservableList<String> listRecords = FXCollections.observableArrayList();

	private Timeline timeline;

	@FXML
	public void handleUp(ActionEvent event) {
		changeOverFile();
	}

	/**
	 * 一つ下のファイルに移動する
	 */
	private void changeOverFile() {
		if (pivot == 0) {
			listView.getSelectionModel().select(1);
		} else {
			pivot = (pivot + size - 1) % size;
			listView.getSelectionModel().select(pivot + 1);
		}
		initField();
	}

	@FXML
	public void handleDown(ActionEvent event) {
		changeUnderFile();
	}

	/**
	 * 一つ上のファイルに移動する
	 */
	private void changeUnderFile() {
		pivot = (pivot + 1) % size;
		listView.getSelectionModel().select(pivot - 1);
		initField();
	}

	@FXML
	public void handleEnter(ActionEvent event) {
		openDirectory();
	}

	/**
	 * 現在該当するディレクトリを開く
	 */
	private void openDirectory() {
		//現在のディレクトリのパス
		String currentDirectory = DataUtil.getCurrentDirectory();
		//works直下のいずれかの作品名
		String fileName = dataMap.get(pivot + 1).get("path");
		//選択した作品のパス
		String path = new StringJoiner("/").add(currentDirectory).add(StringUtil.WORK_DIRECTORY_NAME).add(fileName)
				.toString();
		ProcessBuilder processBuilder = new ProcessBuilder("open", path);
		try {
			Process process = processBuilder.start();
			process.waitFor();
			process.destroy();
		} catch (IOException | InterruptedException e) {
			ErrorUtil.getInstance().printLog(e);
		}

	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		root = XMLUtil.getInstance().getRoot();

		dataMap = new HashMap<Integer, HashMap<String, String>>();

		//ルート要素の子ノードを取得する
		NodeList rootChildren = root.getChildNodes();
		for (int i = 0; i < rootChildren.getLength(); i++) {
			//i個目の作品情報を取得
			Node node = rootChildren.item(i);
			HashMap<String, String> map = new HashMap<>();
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				Element element = (Element) node;
				if (element.getNodeName().equals("work")) {
					NodeList personChildren = node.getChildNodes();
					for (int j = 0; j < personChildren.getLength(); j++) {
						Node personNode = personChildren.item(j);
						if (personNode.getNodeType() == Node.ELEMENT_NODE) {
							setData(personNode, map, "name", "creator", "description", "path");
						}
					}
				}
			}
			if (!map.isEmpty()) {
				dataMap.put((i / 2) + 1, map);
			}
		}

		size = dataMap.size();
		pivot = 0;
		initField();
		initKeyConfig();
		initListView();
		//画面サイズの設定
		imageView.setPreserveRatio(true);
		imageView.setFitHeight(StringUtil.IMAGE_HEIGHT);
		imageView.setFitWidth(StringUtil.IMAGE_WIDTH);

		if (PlatformUtil.isMac()) {
			descriptionText.setFont(Font.font("YuGothic"));
		} else if (PlatformUtil.isWindows()) {
			descriptionText.setFont(Font.font("Meiryo"));
		}
	}

	/**
	 * ListViewの初期化処理
	 */
	private void initListView() {
		listView.setItems(listRecords);
		listView.getSelectionModel().selectFirst();
		listView.setOnMouseClicked(event -> {
			if (event.getClickCount() == 2) {
				openDirectory();
			} else {
				pivot = listView.getSelectionModel().getSelectedIndex();
				initField();
			}
		});
	}

	/**
	 * キー情報の初期化
	 */
	private void initKeyConfig() {
		listView.setOnKeyPressed(event -> {
			KeyCode code = event.getCode();
			if (KeyCode.ENTER.equals(code)) {
				openDirectory();
			} else if (KeyCode.DOWN.equals(code)) {
				changeUnderFile();
			} else if (KeyCode.UP.equals(code)) {
				changeOverFile();
			}
		});
	}

	/**
	 * Fieldの初期化
	 */
	private void initField() {
		HashMap<String, String> map = dataMap.get(pivot + 1);
		if (map != null) {
			workName.setText("作品名:" + map.get("name"));
			creatorName.setText("製作者:" + map.get("creator"));
			String descriptionValue = map.get("description");
			int limit = 30;//1行に何文字まで表示するか
			if (descriptionValue.length() < limit) {
				descriptionText.setText(descriptionValue);
			} else {
				String crlf = System.getProperty("line.separator");
				StringJoiner joiner = new StringJoiner(crlf);
				for (int i = 0; limit * i + limit < descriptionValue.length(); i++) {
					joiner.add(descriptionValue.substring(limit * i, limit * i + limit));
				}
				descriptionText.setText(joiner.toString());
			}

			//画像のパスを分割
			String[] split = map.get("image").split(",");
			imageView.setImage(new Image(split[0]));

			//すでにアニメーションが行われていた場合削除する
			if (timeline != null) {
				timeline.stop();
			}

			timeline = new Timeline(new KeyFrame(new Duration(1000), event -> {
				imageView.setImage(new Image(split[1]));
			}), new KeyFrame(new Duration(2000), event -> {
				imageView.setImage(new Image(split[2]));
			}), new KeyFrame(new Duration(3000), event -> {
				imageView.setImage(new Image(split[0]));
			}));
			//アニメーションの無限ループ
			timeline.setCycleCount(Timeline.INDEFINITE);
			timeline.play();
		} else {
			System.out.println("error:" + pivot);
			//			再現方法:1にカーソルがあるが、実際は6が表示されているときに6を開き、その後下に行こうとすると発生する
			//			対策として最初から最後に移動できないようにした
		}
	}

	/**
	 * 対応する情報をHashMapに格納する
	 */
	private void setData(Node personNode, HashMap<String, String> map, String... args) {
		for (String name : args) {
			if (personNode.getNodeName().equals(name)) {

				if (personNode.getNodeName().equals("name")) {
					listRecords.add(personNode.getTextContent());
					//TODO:全部の作品の画像数があっているかの確認(フィールドにパラメータを設置して対応してもよい)

					//現在のディレクトリのパス
					String currentDirectory = DataUtil.getCurrentDirectory();

					//選択した作品のパス
					String path = new StringJoiner("/").add(currentDirectory).add(StringUtil.WORK_DIRECTORY_NAME)
							.add(personNode.getTextContent()).add("sample").toString();

					//選択した作品のパス
					//					String path = new StringJoiner("/").add(currentDirectory).add(StringUtil.WORK_DIRECTORY_NAME)
					//							.add(personNode.getTextContent()).add("sample").toString();

					//					Path path2;
					//					path2 = Paths.get(new File("").getAbsoluteFile().toURI());
					//					try (Stream<Path> stream = Files.list(path2)) {
					//						StringJoiner stringJoiner = new StringJoiner(",");
					//						stream
					//								.filter(entry -> entry.getFileName().toString().endsWith("works"))
					//								.forEach(System.out::println);
					//					} catch (IOException e) {
					//						// TODO Auto-generated catch block
					//						e.printStackTrace();
					//					}

					File[] list = new File(path).listFiles();
					StringJoiner joiner = new StringJoiner(",");
					if (!Objects.isNull(list)) {
						for (File fileName : list) {
							joiner.add(new StringJoiner("/").add(StringUtil.WORK_DIRECTORY_NAME)
									.add(personNode.getTextContent()).add("sample")
									.add(fileName.getName()).toString());
						}
						map.put("image", joiner.toString());
					} else {
						System.out.println("pass miss");
					}
					map.put(name, personNode.getTextContent());
					//TODO:名前は外部,画像は内部(内部に統一)
				} else {
					map.put(name, personNode.getTextContent());
				}
			}
		}
	}

}
