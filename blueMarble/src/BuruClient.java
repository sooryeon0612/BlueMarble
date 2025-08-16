import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Line2D;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Scanner;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

//클라이언트에서 서버로 메시지를 전송하는 클래스
class ClientSender {
    Socket socket;
    DataOutputStream out;
    String name;

    ClientSender(Socket socket) {
        this.socket = socket;
        try {
            out = new DataOutputStream(socket.getOutputStream());
            name = "["+socket.getInetAddress()+":"+socket.getPort()+"]";
        } catch(Exception e) {}
    }

    void sendMessage(String message) {
    	try {
			out.writeUTF(message);
			out.flush();
			System.out.println("[msg sent] " + message);
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
}

// 서버로부터 온 메시지를 받는 클래스
class ClientReceiver extends Thread {
    Socket socket;
    DataInputStream in;

    ClientReceiver(Socket socket) {
        this.socket = socket;
        try {
            in = new DataInputStream(socket.getInputStream());
        } catch(IOException e) {}

    }
    
    // 서버에서 받은 메시지에 따라 클라이언트에서 수행해야 할 동작들
    public void run() {
        while(in!=null) {
            try {
            	String s = in.readUTF();
                System.out.println("[from서버] " + s);
                System.out.println("nowP = " + BuruClient.nowP);
                
                if(s.equals("주사위던지는중")) {
					BuruClient.myCanvas.rollingA();
					BuruClient.myCanvas.rollingB();
					BuruClient.play("sounds/주사위소리.wav");
                } else if(s.startsWith("주사위(")) {
                	s = s.replace("주사위(","").replace(")","");
                	int dice1 = Integer.parseInt(s.split(",")[0]);
                	int dice2 = Integer.parseInt(s.split(",")[1]);
                	BuruClient.myCanvas.setRoll(new int[] {dice1, dice2});
                	BuruClient.myCanvas.movePlayer(BuruClient.nowP, dice1+dice2);
                	BuruClient.myCanvas.repaint();
                } else if(s.startsWith("땅인수(")) {
                	s = s.replace("땅인수(","").replace(")","");
                	int numOfDdang = Integer.parseInt(s);
                	BuruClient.buyCountry("P1", numOfDdang);
                	BuruClient.spendMoney("P1", numOfDdang);
                	BuruClient.myCanvas.repaint();
                } else if(s.startsWith("건물구입(")) {
                	s = s.replace("건물구입(","").replace(")","");
                	int numOfDdang = Integer.parseInt(s);
                	BuruClient.buildUp("P1", numOfDdang);
	   				BuruClient.spendMoney("P1", numOfDdang);
                	BuruClient.myCanvas.repaint();
                } else if(s.startsWith("돈P")) {
                	BuruClient.p1Money = Integer.parseInt(s.replace("돈P1:",""));
                	BuruClient.myCanvas.repaint();
                } else if(s.endsWith("끝")) {
                	JOptionPane.showMessageDialog(null, "이겼습니다!");
                	JOptionPane.showMessageDialog(null, "[패자]P1 " + "[돈]" + BuruClient.p1Money + " [건물수]" + BuruClient.countBuilding("P1") 
						+ "\n[승자]P2 " + "[돈]" + BuruClient.p2Money + " [건물수]" + BuruClient.countBuilding("P2"));
                	JOptionPane.showMessageDialog(null, "게임을 종료합니다.");
                	System.exit(0);
                } else if(s.startsWith("사용자:")) {
                	BuruClient.nickName = s.replace("사용자:", "");
                	BuruClient.updateGame();
                } else if(s.equals("저장")) {
                	JOptionPane.showMessageDialog(null, "저장했습니다!\n게임을 종료합니다.");
					System.exit(0);
                }
            } catch(Exception e) {}
        }
    } // run
}

public class BuruClient extends JFrame {
	static ClientSender sender = null;
	static ClientReceiver receiver = null;
	static MyCanvas myCanvas = null;
	
	// 주사위 소리
	public static void play(String fileName) {
        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(new File(fileName));
            Clip clip = AudioSystem.getClip();
            clip.stop();
            clip.open(ais);
            clip.start();
        }
        catch (Exception e) { }
    }
	
	static boolean loading = false;
	
	// 게임판을 최신 상태로 업데이트
	static void updateGame() throws Exception {
		loading = true;
		if(!findUser(nickName,"buru").equals("None")) {
			myCanvas.repaint();
			nowP = getTurn(nickName) == 1 ? "P1" : "P2";
			p1Loc = getP1Loc(nickName);
			p2Loc = getP2Loc(nickName);
			p1Money = getP1Money(nickName);
			p2Money = getP2Money(nickName);
			loadBuilding(nickName);
			myCanvas.a += p1Loc;
			myCanvas.b += p2Loc;
			myCanvas.repaint();
		}
		loading = false;
	}
	
	// 게임판 초기 세팅 & 접속 
	 public BuruClient() throws Exception {  
        setTitle("부루마블 Client");
        myCanvas = new MyCanvas();
        getContentPane().add(myCanvas, BorderLayout.CENTER); 
        setSize(700, 630);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);  
        setVisible(true);
        
        System.out.println("[클라이언트] 접속하기");
        Socket socket = new Socket("localhost", 7042);
        System.out.println("[클라이언트] 접속 완료!");
        sender = new ClientSender(socket);
        receiver = new ClientReceiver(socket);
        receiver.start();
    }
	 
	public static class MyCanvas extends Canvas {
	    	private Image imgBG, start;
	    	private Image athens, berlin, buenos, hawaii, korea, madrid, newyork, paris, sydney, taipei, tokyo;
	    	private Image player1, player2;
	    	private Image dice1, dice2, dice3, dice4, dice5, dice6;
	    	private Image building;
	    	private int x, y;
	    	int a, b;
	    	private int[] roll = new int[2];
	    	private boolean click = true;
			private boolean movingPlayerInCanvas = false;
			
			// 게임판 이미지 세팅 
	    	public MyCanvas() { 
	    		imgBG = new ImageIcon("imgs/부루마불.png").getImage();
	    		start = new ImageIcon("imgs/start.png").getImage();
	    		
	    		athens = new ImageIcon("imgs/athens.png").getImage();
	    		berlin = new ImageIcon("imgs/berlin.png").getImage();
	    		buenos = new ImageIcon("imgs/buenos.png").getImage();
	    		hawaii = new ImageIcon("imgs/hawaii.png").getImage();
	    		korea = new ImageIcon("imgs/korea.png").getImage();
	    		madrid = new ImageIcon("imgs/madrid.png").getImage();
	    		newyork = new ImageIcon("imgs/newyork.png").getImage();
	    		paris = new ImageIcon("imgs/paris.png").getImage();
	    		sydney = new ImageIcon("imgs/sydney.png").getImage();
	    		taipei = new ImageIcon("imgs/taipei.png").getImage();
	    		tokyo = new ImageIcon("imgs/tokyo.png").getImage();
	    		
	    		player1 = new ImageIcon("imgs/플레이어1.png").getImage();
	    		player2 = new ImageIcon("imgs/플레이어2.png").getImage();
	    		
	    		dice1 = new ImageIcon("imgs/dice1.png").getImage();
	    		dice2 = new ImageIcon("imgs/dice2.png").getImage();
	    		dice3 = new ImageIcon("imgs/dice3.png").getImage();
	    		dice4 = new ImageIcon("imgs/dice4.png").getImage();
	    		dice5 = new ImageIcon("imgs/dice5.png").getImage();
	    		dice6 = new ImageIcon("imgs/dice6.png").getImage();
	    		
	    		building = new ImageIcon("imgs/별장.png").getImage();
	    		
	    		this.addMouseListener(new MouseListener() {
					
	    			// 마우스 클릭 이벤트 (저장을 클릭하면 게임을 저장함)
					@Override
					public void mouseClicked(MouseEvent e) {
						int x = e.getX();
						int y = e.getY();
						
						if((x>=0 && x<=70) && (y>=0 && y<=30)) {
							int answer = JOptionPane.showConfirmDialog(null, "저장하시겠습니까?", "저장", JOptionPane.YES_NO_OPTION);
							if(answer == 0) {
								sender.sendMessage("저장");
								JOptionPane.showMessageDialog(null, "저장했습니다!\n게임을 종료합니다.");
								System.exit(0);
							}
						}
					}
					
					// 게임판을 꾹 누르면 주사위가 돌아감
					@Override
					public void mousePressed(MouseEvent e) {
						System.out.println("mousePressed에서 nowP : " + nowP);
						if(nowP.equals("P1"))
							return;
						
						int x = e.getX();
						int y = e.getY();
						
						if((x>=220 && x<=470) && (y>=200 && y<=400)) {
							click = false; 
							rollingA();
							rollingB();
							play("sounds/주사위소리.wav");
						}
						sender.sendMessage("주사위던지는중");
					}

					// 마우스 떼면 말 이동 
					@Override
					public void mouseReleased(MouseEvent e) {
						System.out.println("mouseReleased에서 nowP : " + nowP);

						if(nowP.equals("P1"))return;
						
						click = true; 

						p2Loc += roll[0] + roll[1];
						nowLoc = p2Loc % 12;
						sender.sendMessage("주사위(" + roll[0] + "," + roll[1] + ")");

						movePlayer(nowP, roll[0] + roll[1]); 
					}

					@Override
					public void mouseEntered(MouseEvent e) {
					}

					@Override
					public void mouseExited(MouseEvent e) {
					}

				});
	    		
	    	}
	    	
	    	// 첫 번째 주사위와 두 번쨰 주사위를 서버에서 받아온 값대로 세팅 
			public void setRoll(int[] dices) {
				roll[0] = dices[0];
				roll[1] = dices[1];
			}

			// 게임판에 변화가 있을 떄 게임판을 새로 그림
	        public void paint(Graphics g) {
	        	super.paint(g);
	            
	        	g.drawImage(imgBG, 100, 75, 500, 480, null); // 나라 그리기 
	        	g.drawImage(start, 0, 0, 175, 150, null);
	        	g.drawImage(taipei, 175, 0, 175, 150, null);
	        	g.drawImage(berlin, 350, 0, 175, 150, null);
	        	g.drawImage(athens, 525, 0, 175, 150, null);
	        	g.drawImage(newyork, 525, 150, 175, 150, null);
	        	g.drawImage(tokyo, 525, 300, 175, 150, null);
	        	g.drawImage(madrid, 525, 450, 175, 150, null);
	        	g.drawImage(hawaii , 350, 450, 175, 150, null);
	        	g.drawImage(buenos, 175, 450, 175, 150, null);
	        	g.drawImage(sydney, 0, 450, 175, 150, null);
	        	g.drawImage(paris, 0, 300, 175, 150, null);
	        	g.drawImage(korea, 0, 150, 175, 150, null);
	        	
	        	for(int i=0; i<4; i++) { //건물 그리기 
	        		for(int j=0; j<4; j++) {
	        			if(buildingFloor[i][j] >= 1) g.drawImage(building, b1Loc[map[i][j]][0], b1Loc[map[i][j]][1], 30, 30, null);
	        			if(buildingFloor[i][j] >= 2) g.drawImage(building, b2Loc[map[i][j]][0], b2Loc[map[i][j]][1], 30, 30, null);
	        			if(buildingFloor[i][j] == 3) g.drawImage(building, b3Loc[map[i][j]][0], b3Loc[map[i][j]][1], 30, 30, null);
	        		}
	        	}
	        	
	        	switch(roll[0]) { // 첫번쨰 주사위
	       		case 1: g.drawImage(dice1, 177, 154, 100, 100, null); break;
	        	case 2: g.drawImage(dice2, 177, 154, 100, 100, null); break;
	       		case 3: g.drawImage(dice3, 177, 154, 100, 100, null); break;
	       		case 4: g.drawImage(dice4, 177, 154, 100, 100, null); break;
	       		case 5: g.drawImage(dice5, 177, 154, 100, 100, null); break;
	       		case 6: g.drawImage(dice6, 177, 154, 100, 100, null); break;
	        	}
	        	
	        	switch(roll[1]) { //두번째 주사위 
	       		case 1: g.drawImage(dice1, 425, 154, 100, 100, null); break;
	        	case 2: g.drawImage(dice2, 425, 154, 100, 100, null); break;
	       		case 3: g.drawImage(dice3, 425, 154, 100, 100, null); break;
	       		case 4: g.drawImage(dice4, 425, 154, 100, 100, null); break;
	       		case 5: g.drawImage(dice5, 425, 154, 100, 100, null); break;
	       		case 6: g.drawImage(dice6, 425, 154, 100, 100, null); break;
	        	}
	        	
	        	g.drawImage(player1, mapP1Loc[a][0], mapP1Loc[a][1], 130, 130, null); //플레이어 위치 
	        	g.drawImage(player2, mapP2Loc[b][0], mapP2Loc[b][1], 130, 130, null);
	        
	        	g.setColor(Color.DARK_GRAY);
	        	g.drawString("P1 : " + p1Money + "원", 200, 400);
	        	g.drawString("P2 : " + p2Money + "원", 400, 400);
	        	
	        	g.setColor(Color.black);
	        	g.fillRect(0, 0, 70, 30);
	        	g.setColor(Color.white);
	        	g.drawString("저장 후 종료", 0, 20);
	        	
	        	if(nowP.equals("P1")) {
	        		g.setColor(Color.white);
	        		g.fillRect(200, 280, 290, 80);
	        		Font font =  new Font("Arial", Font.ITALIC, 30);
	        		g.setFont(font);
	        		g.setColor(Color.black);
	        		g.drawString(loading ? "데이터 로딩중..." : "P1이 플레이 중입니다.", 215, 320);
	        	}
	        	
	        	Graphics2D g2 = (Graphics2D)g;
	        	for(int i=1; i<12; i++) { // 소유주 표시하기 
	        		if(p1Building[i][0] == 1) {
	        			g2.setPaint(Color.yellow);
	    	        	g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.5f));
	    	        	int x1 = mapP1Loc[i][0];
	    	        	int y1 = mapP1Loc[i][1];
	    	        	int x2 = x1 + 175 -1;
	    	        	int y2 = y1 + 150 -1;
	    	        	g2.fillRect(x1, y1, 175, 40);
	    	        	g2.fillRect(x1, y1+40, 40, 150-81);
	    	        	g2.fillRect(x2-39, y1+40, 40, 150-81);
	    	        	g2.fillRect(x1, y2-40, 175, 40);
	        		}
	        		if(p2Building[i][0] == 1) {
	        			g2.setPaint(Color.cyan);
	    	        	g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.5f));
	    	        	int x1 = mapP1Loc[i][0];
	    	        	int y1 = mapP1Loc[i][1];
	    	        	int x2 = x1 + 175 -1;
	    	        	int y2 = y1 + 150 -1;
	    	        	g2.fillRect(x1, y1, 175, 40);
	    	        	g2.fillRect(x1, y1+40, 40, 150-81);
	    	        	g2.fillRect(x2-39, y1+40, 40, 150-81);
	    	        	g2.fillRect(x1, y2-40, 175, 40);
	        		}
	        	}
	        }
	        
	       public void rollingA () {	// 첫 번째 주사위
				Timer t = new Timer(50, new AbstractAction() {
				    @Override
				    public void actionPerformed(ActionEvent ae) {
				    	if(!click) {
					    	roll[0] = (int)(Math.random()*6+1);
					    	repaint();
				    	} else {
				            ((Timer) ae.getSource()).stop();
				        } 
				    }
				});
				t.start();
			}
	       
	       public void rollingB () {	// 두 번째 주사위
				Timer t = new Timer(50, new AbstractAction() {
				    @Override
				    public void actionPerformed(ActionEvent ae) {
				    	if(!click) {
					    	roll[1] = (int)(Math.random()*6+1);
					    	repaint();
				    	} else {
				            ((Timer) ae.getSource()).stop();
				        }
				    }
				});
				t.start();
			}
	       
	       // 주사위가 다 돌아가고 나서 플레이어 옮김 
	       private void doWhenTimerEnds() {
				if(nowP.equals("P2")) purchase();			
				checkEnd();
				
				if(nowP.equals("P1")) nowP = "P2";
				else nowP = "P1";
	       }
	       
	       // nowP를 times칸만큼 이동.
	       public void movePlayer(String nowP, int times) {  
 	    	   if(nowP.equals("P1")) {
	    		   Timer t = new Timer(200, new AbstractAction() {
	    			   int cnt = 0;
					    @Override
					    public void actionPerformed(ActionEvent ae) {
				    		a = ++a % 12;
				    		repaint();
				    		if(a == 0) p1Money += 20;
				    		if(++cnt >= times) {
				    			((Timer) ae.getSource()).stop();
				    			doWhenTimerEnds();
				    		}
					    }
					});
					t.start();
	    	   } else {
	    		   Timer t = new Timer(200, new AbstractAction() {
	    			   int cnt = 0;
					    @Override
					    public void actionPerformed(ActionEvent ae) {
				    		b = ++b % 12;
				    		repaint();
				    		if(b == 0) {
				    			JOptionPane.showMessageDialog(null, "출발에 도착했습니다.\n20원을 획득합니다!");
				    			p2Money += 20;
				    		}
				    		if(++cnt >= times) {
				    			((Timer) ae.getSource()).stop();
				    			doWhenTimerEnds();
				    		}
					    }
					});
					t.start();
	    	   }
			}
	       
	       // 땅과 건물 구매하기 
	       public void purchase() {  // 클라이언트에서는 nowP = "P2"일 떄에만 실행.
	    	   if(nowLoc == 0) return;
	    	   // 1. 빈 땅일 경우 
	    	   if(p1Building[nowLoc][0] == 0 && p2Building[nowLoc][0] == 0) { // 소유주가 없음 
	   				int answer = -2;
	   				answer = JOptionPane.showConfirmDialog(null, "땅을 인수하시겠습니까?", "나라 인수", JOptionPane.YES_NO_OPTION);
	   				if(answer == 0) {
		   				buyCountry(nowP, nowLoc);
		   				spendMoney(nowP, nowLoc);
		   				JOptionPane.showMessageDialog(null, "땅을 인수했습니다!\n" + 
	   		   				findPrice(nowLoc) + "원이 차감되었습니다.(잔액:" + (nowP.equals("P1") ? p1Money + ")" : p2Money + ")"));
	   				}
	   				BuruClient.sender.sendMessage("땅인수(" + nowLoc + ")");
//	   				2.P1땅일 경우 
	   			} 
	    	   else if(p1Building[nowLoc][0] == 1){ // P1땅임 
	   				int passFee = price[mapX(nowLoc)][mapY(nowLoc)] * 2;
		    		   p2Money -= passFee;
		    		   JOptionPane.showMessageDialog(null, "P2 통행료 차감 : " + passFee + "원");
		    		   BuruClient.sender.sendMessage("돈P2:" + p2Money);
		    		   repaint();
//		    		   3.내 땅이고 빌딩이 3층 이하 
	   			} else if(checkHigh(nowP, nowLoc)) {
	   				int answer = -2;
	    			answer = JOptionPane.showConfirmDialog(null, "건물을 올리시겠습니까?", "건물 구매", JOptionPane.YES_NO_OPTION);
	    			if(answer == 0) {
		   				buildUp(nowP, nowLoc);
		   				spendMoney(nowP, nowLoc);
		   				JOptionPane.showMessageDialog(null, "건물을 올렸습니다!\n" + 
	   						findPrice(nowLoc) + "원이 차감되었습니다.(잔액:" + (nowP.equals("P1") ? p1Money + ")" : p2Money + ")"));
	    			}
	   				BuruClient.sender.sendMessage("건물구입(" + nowLoc + ")");
	   				repaint();
	   			}
	    	   repaint();
	   			
	       }
	       
	       // 파산 체크
	       public void checkEnd() {
	    	   if(p2Money < 0) {
	    		   JOptionPane.showMessageDialog(null, "파산하였습니다");
	    		   BuruClient.sender.sendMessage("P2끝");
	    		   JOptionPane.showMessageDialog(null, "[승자]P1 " + "[돈]" + p1Money + " [건물수]" + countBuilding("P1") 
	    		   									+ "\n[패자]P2 " + "[돈]" + p2Money + " [건물수]" + countBuilding("P2"));
	    		   JOptionPane.showMessageDialog(null, "패배하였습니다.\n게임을 종료합니다.");
	    		   System.exit(0);
	    	   }
	       }
	 }

	 static int[][] mapP1Loc = { //게임판 좌표값 
				{0,0}, {175, 0}, {350, 0}, {525, 0}, {525, 150}, {525, 300}, {525, 450}, {350, 450}, {175, 450}, {0, 450}, {0, 300}, {0, 150}};
	 
	 static int[][] mapP2Loc = new int[12][2];
	 static int[][] b1Loc = new int[12][2];
	 static int[][] b2Loc = new int[12][2];
	 static int[][] b3Loc = new int[12][2];
	 
	 // 게임 시작 
	 static void showMap() {
		 SwingUtilities.invokeLater(new Runnable(){
	            public void run()  {
	            	BuruClient jFrame = null;
					try {
						jFrame = new BuruClient();
					} catch (Exception e) {
						e.printStackTrace();
					}
	                jFrame.setVisible(true);
	            }
	        });
	 }
	static int p1Loc = 0, p2Loc = 0, nowLoc = 0;
	static String nowP = "P1";
	static String nickName = "";
	
	public static void main(String[] args) throws Exception{
		Scanner sc = new Scanner(System.in);
		
		for(int i=0; i<12; i++) {
			for(int j=0; j<2; j++) {
				mapP2Loc[i][j] = mapP1Loc[i][j] + 30;
				b1Loc[i][j] = j == 0 ? mapP1Loc[i][j] + 140 : mapP1Loc[i][j] + 40;
				b2Loc[i][j] = j == 0 ? mapP1Loc[i][j] + 140 : mapP1Loc[i][j] + 70;
				b3Loc[i][j] = j == 0 ? mapP1Loc[i][j] + 140 : mapP1Loc[i][j] + 100;
			}
		}

		//게임판 그려줌 
		boolean flag = true;
		System.out.println(nickName);
		
		while(true) {	//게임 시작	
		showMap();
		
//		턴 시작 전 종료할지 말지 선택  
		System.out.print("아무 키 + Enter로 주사위를 돌립니다.(Exit을 입력해 게임 종료.)\n>>");
		String goStop = sc.next();
		goStop = goStop.toUpperCase();
		if(goStop.equals("EXIT")) {
			flag = false;
			break;
		}		
//		구매 전 플레이어 데이터 
		showData(nowP);
		} //while문
		
//		EXIT을 눌러 종료함 
		if(!flag) {
			System.out.print("1. 저장하기\n2. 나가기\n>>");
			int n = sc.nextInt();
			if(n == 1) {
				saveBuru(nickName, nowP, p1Loc, p2Loc);
				getBuildingOwn(nickName);
				getBuildingFloor(nickName);
				System.out.println("저장을 완료했습니다!\n게임을 종료합니다.");
			} else System.out.println("게임을 종료합니다.");
			}
		}
	
	static String[][] country = { //나라
			{"시작", "이란", "캐나다", "필리핀"}, 
			{"한국", "   ", "   ", "영국"},
			{"독일", "   ", "   ", "일본"},
			{"인도", "중국", "미국", "베트남"}};

	static int[][] map = { //게임판 인덱스
			{0, 1, 2, 3}, 
			{11, -1, -1, 4},
			{10, -1, -1, 5},
			{9, 8, 7, 6}};
	
	static int[][] price = { //가격 인덱스
			{0, 5, 18, 14}, 
			{100, -1, -1, 35},
			{32, -1, -1, 30},
			{24, 22, 26, 38}};
	
		static int[][] buildingOX = { //빌딩유무
		{-1, 0, 0, 0}, 
		{0, -1, -1, 0},
		{0, -1, -1, 0},
		{0, 0, 0, 0}};

	static int[][] buildingFloor = { //빌딩층수
			{0, 0, 0, 0}, 
			{0, -1, -1, 0},
			{0, -1, -1, 0},
			{0, 0, 0, 0}};
	
	static int[][] p1Building = new int[12][2];
	static int[][] p2Building = new int[12][2];
	static int p1Money = 500, p2Money = 500;
	
	//주사위 돌려서 움직일 거리 구해줌
	static int[] rollDice(String nowP) { 
		System.out.print("[" + nowP + "]");
		int[] roll = new int[2];
		roll[0] = (int)(Math.random() * 6 + 1);
		roll[1] = (int)(Math.random() * 6 + 1);
		System.out.println(roll[0] + "," + roll[1] + " => " + (roll[0] + roll[1]) + " 이동!");
		return roll;
	}
	
	//건물 주인이 맞는지
	static boolean checkOwn(String nowP, int nowLoc) { 
		if(nowP.equals("P1")) return p1Building[nowLoc][0] == 1 ? true : false;
		else return p2Building[nowLoc][0] == 1 ? true : false;
	}
	
	//그 위치에 건물이 있는지 없는지
	static boolean buildExist(String nowP, int nowLoc) { 
		int mapX = 0, mapY = 0;
		for(int i=0; i<4; i++) {
			for(int j=0; j<4; j++) {
				if(nowLoc == map[i][j]) {
					mapX = i;
					mapY = j;
				}
			}
		}
		return buildingOX[mapX][mapY] == 0 ? false : true;
	}
	
	//사려는 위치의 건물이 3층미만인지
	static boolean checkHigh (String nowP, int nowLoc) { 
		if(nowP.equals("P1")) return p1Building[nowLoc][1] < 3 ? true : false;
		else return p2Building[nowLoc][1] < 3 ? true : false;
	}
	
	// 건물 층수 올리기
	static void buildUp (String nowP, int nowLoc) {
		buildingFloor[mapX(nowLoc)][mapY(nowLoc)]++;
		if(nowP.equals("P1")) p1Building[nowLoc][1]++;
		else p2Building[nowLoc][1]++;
	}
	
	// 땅 구매 
	static void buyCountry(String nowP, int nowLoc) {
		buildingOX[mapX(nowLoc)][mapY(nowLoc)]++;
		if(nowP.equals("P1")) p1Building[nowLoc][0]++;
		else p2Building[nowLoc][0]++;
	}
	
	// 땅이 누구 수요인지 
	static String showOwn(int i, int j) {
		if(p1Building[map[i][j]][0] == 1) return "[P1]";
		else if (p2Building[map[i][j]][0] == 1) return "[P2]";
		else return "";
	}
	
	// 돈 차감 
	static void spendMoney(String nowP, int nowLoc) {
		if(nowP.equals("P1")) p1Money -= price[mapX(nowLoc)][mapY(nowLoc)];
		else  p2Money -= price[mapX(nowLoc)][mapY(nowLoc)];
	}
	
	// 잔액 표시 
	static void showData (String nowP) {
		if(nowP.equals("P1")) {
			System.out.println("[Player 1]");
			System.out.println("잔액 : " + p1Money);
		}else {
			System.out.println("[Player 2]");
			System.out.println("잔액 : " + p2Money);
		}
	}
	
	// 땅 가격 찾기 
	static int findPrice(int nowLoc) {
		return price[mapX(nowLoc)][mapY(nowLoc)];
	}
	
	// 한 플레이어가 소유한 건물이 몇 개인지 
	static int countBuilding(String player) {
		int count = 0;
		if(player.equals("P1")) {
			for(int i=0; i<p1Building.length; i++) {
				count += p1Building[i][0] != 0 ? 1 : 0;
			}
		}else {
			for(int i=0; i<p2Building.length; i++) {
				count += p2Building[i][0] != 0 ? 1 : 0;
			}
		}
		return count;
	}
	
	// 땅 인덱스로 게임판에서 인덱스의 x좌표 찾기 
	static int mapX(int idx) {
		for(int i=0; i<4; i++) {
			for(int j=0; j<4; j++) {
				if(idx == map[i][j]) return i;
			}
		}
		return -1;
	}
	
	// 땅 인덱스로 게임판에서 인덱스의 y좌표 찾기 
	static int mapY(int idx) {
		for(int i=0; i<4; i++) {
			for(int j=0; j<4; j++) {
				if(idx == map[i][j]) return j;
			}
		}
		return -1;
	}
	
	// 게임 저장 
	static void saveBuru(String nickName, String nowP, int p1Loc, int p2Loc) throws Exception {
		String driver = "oracle.jdbc.driver.OracleDriver";
		String url = "jdbc:oracle:thin:@localhost:1521:xe";
		String id = "scott";
		String pw = "tiger";
		Class.forName(driver);
		Connection conn = DriverManager.getConnection(url, id, pw);
		
		String sqlNew = "INSERT INTO BURU VALUES ('"+ nickName +"', ?, ?, ?, ?, ?)";
		String sqlOld = "UPDATE 	buru "
				+       "SET 	turn = ?, p1Loc = ?, p1Money = ?, p2Loc = ?, p2Money = ?"
				+       "WHERE 	nickname = '"+ nickName +"'";
		
		PreparedStatement pstmt = conn.prepareStatement(findUser(nickName, "buru").equals("None") ? sqlNew : sqlOld);
		pstmt.setInt(1, nowPNumber(nowP));
		pstmt.setInt(2, p1Loc % 12);
		pstmt.setInt(3, p1Money);
		pstmt.setInt(4, p2Loc % 12);
		pstmt.setInt(5, p2Money);
		pstmt.executeUpdate();
			
		pstmt.close();
		conn.close();
	}
	
	// 현재 플레이어가 누구인지 
	static int nowPNumber (String nowP) {
		return nowP.equals("P1") ? 1 : 2;
	}
	
	// 건물 주인 저장 
	static void saveBuildingOwn(String nickName, int idx, int own) throws Exception {
		String driver = "oracle.jdbc.driver.OracleDriver";
		String url = "jdbc:oracle:thin:@localhost:1521:xe";
		String id = "scott";
		String pw = "tiger";
		Class.forName(driver);
		Connection conn = DriverManager.getConnection(url, id, pw);
		
		String sql = "UPDATE buildingOwn "
				+    "SET map_"+idx+" = ? "
				+    "WHERE nickname = ?";
		PreparedStatement pstmt = conn.prepareStatement(sql);
		pstmt.setInt(1, own);
		pstmt.setString(2, nickName);
		pstmt.executeUpdate();
			
		pstmt.close();
		conn.close();
	}
	
	// 건물 소유주 저장하기 
	static void makeBuildingOwn(String nickName) throws Exception {
		String driver = "oracle.jdbc.driver.OracleDriver";
		String url = "jdbc:oracle:thin:@localhost:1521:xe";
		String id = "scott";
		String pw = "tiger";
		Class.forName(driver);
		Connection conn = DriverManager.getConnection(url, id, pw);
		
		String sql = "INSERT INTO buildingOwn VALUES (?, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)";
		PreparedStatement pstmt = conn.prepareStatement(sql);
		pstmt.setString(1, nickName);
		pstmt.executeUpdate();
			
		pstmt.close();
		conn.close();
	}
	
	// 유저의 건물 소유 정보를 초기화하고 현재 보유 상태를 저장
	static void getBuildingOwn(String nickName) throws Exception {
		if(findUser(nickName, "buildingown").equals("None")) makeBuildingOwn(nickName);		
		for(int i=0; i<p1Building.length; i++) {
			if(p1Building[i][0] != 0) saveBuildingOwn(nickName, i, 1);
			if(p2Building[i][0] != 0) saveBuildingOwn(nickName, i, 2);
		}
	}
	
	// 유저의 건물 층수 정보를 초기화하고 현재 보유 상태를 저장
	static void getBuildingFloor(String nickName) throws Exception {
		if(findUser(nickName, "buildingfloor").equals("None")) makeBuildingFloor(nickName);		
		for(int i=0; i<p1Building.length; i++) {
			if(p1Building[i][0] != 0) saveBuildingFloor(nickName, i, p1Building[i][1]);
			if(p2Building[i][0] != 0) saveBuildingFloor(nickName, i, p2Building[i][1]);
		}
	}
	
	// 건물 올리기 
	static void makeBuildingFloor(String nickName) throws Exception {
		String driver = "oracle.jdbc.driver.OracleDriver";
		String url = "jdbc:oracle:thin:@localhost:1521:xe";
		String id = "scott";
		String pw = "tiger";
		Class.forName(driver);
		Connection conn = DriverManager.getConnection(url, id, pw);
		
		String sql = "INSERT INTO buildingFloor VALUES (?, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)";
		PreparedStatement pstmt = conn.prepareStatement(sql);
		pstmt.setString(1, nickName);
		pstmt.executeUpdate();
			
		pstmt.close();
		conn.close();
	}
	
	// 쌓은 건물 층수 저장 
	static void saveBuildingFloor(String nickName, int idx, int floor) throws Exception {
		String driver = "oracle.jdbc.driver.OracleDriver";
		String url = "jdbc:oracle:thin:@localhost:1521:xe";
		String id = "scott";
		String pw = "tiger";
		Class.forName(driver);
		Connection conn = DriverManager.getConnection(url, id, pw);
		
		String sql = "UPDATE buildingFloor "
				+    "SET map_"+ idx +" = ? "
				+    "WHERE nickname = ?";
		PreparedStatement pstmt = conn.prepareStatement(sql);
		pstmt.setInt(1, floor);
		pstmt.setString(2, nickName);
		pstmt.executeUpdate();
			
		pstmt.close();
		conn.close();
	}
	
	// 기존에 있는 유저인지 없는 유저인지 확인 
	static String findUser(String nickName, String table) throws Exception {
		String driver = "oracle.jdbc.driver.OracleDriver";
		String url = "jdbc:oracle:thin:@localhost:1521:xe";
		String id = "scott";
		String pw = "tiger";
		Class.forName(driver);
		Connection conn = DriverManager.getConnection(url, id, pw);
		
		String sql = "SELECT	* "
				   + "FROM	 " + table;
		PreparedStatement pstmt = conn.prepareStatement(sql);
		pstmt.executeQuery();
		ResultSet rs = pstmt.executeQuery();
		
		while(rs.next()) {
			String name = rs.getString("nickname");
			if(name.equals(nickName)) return name;
		}
		
		rs.close();
		pstmt.close();
		conn.close();
		return "None";
	}
	
	// 저장되어있던 게임이 누구의 턴부터 시작해야 하는지
	static int getTurn(String nickName) throws Exception {
		String driver = "oracle.jdbc.driver.OracleDriver";
		String url = "jdbc:oracle:thin:@localhost:1521:xe";
		String id = "scott";
		String pw = "tiger";
		Class.forName(driver);
		Connection conn = DriverManager.getConnection(url, id, pw);
		
		String sql = "SELECT	* "
				   + "FROM	buru "
				   + "WHERE	nickname = ?";
		PreparedStatement pstmt = conn.prepareStatement(sql);
		pstmt.setString(1, nickName);
		pstmt.executeQuery();
		ResultSet rs = pstmt.executeQuery();
		
		while(rs.next()) {
			int turn = rs.getInt("turn");
			return turn;
		}
		
		rs.close();
		pstmt.close();
		conn.close();
		return -1;
	}
	
	// 첫 번쨰 플레이어의 마지막 저장 위치가 어디였는지 
	static int getP1Loc(String nickName) throws Exception {
		String driver = "oracle.jdbc.driver.OracleDriver";
		String url = "jdbc:oracle:thin:@localhost:1521:xe";
		String id = "scott";
		String pw = "tiger";
		Class.forName(driver);
		Connection conn = DriverManager.getConnection(url, id, pw);
		
		String sql = "SELECT	* "
				   + "FROM	buru "
				   + "WHERE	nickname = ?";
		PreparedStatement pstmt = conn.prepareStatement(sql);
		pstmt.setString(1, nickName);
		pstmt.executeQuery();
		ResultSet rs = pstmt.executeQuery();
		
		while(rs.next()) {
			int p1Loc = rs.getInt("p1loc");
			return p1Loc;
		}
		
		rs.close();
		pstmt.close();
		conn.close();
		return -1;
	}
	
	// 두 번쨰 플레이어의 마지막 저장 위치가 어디였는지 
	static int getP2Loc(String nickName) throws Exception {
		String driver = "oracle.jdbc.driver.OracleDriver";
		String url = "jdbc:oracle:thin:@localhost:1521:xe";
		String id = "scott";
		String pw = "tiger";
		Class.forName(driver);
		Connection conn = DriverManager.getConnection(url, id, pw);
		
		String sql = "SELECT	* "
				   + "FROM	buru "
				   + "WHERE	nickname = ?";
		PreparedStatement pstmt = conn.prepareStatement(sql);
		pstmt.setString(1, nickName);
		pstmt.executeQuery();
		ResultSet rs = pstmt.executeQuery();
		
		while(rs.next()) {
			int p2Loc = rs.getInt("p2loc");
			return p2Loc;
		}
		
		rs.close();
		pstmt.close();
		conn.close();
		return -1;
	}
	
	// 저장되어있던 플레이어1의 돈 
	static int getP1Money(String nickName) throws Exception {
		String driver = "oracle.jdbc.driver.OracleDriver";
		String url = "jdbc:oracle:thin:@localhost:1521:xe";
		String id = "scott";
		String pw = "tiger";
		Class.forName(driver);
		Connection conn = DriverManager.getConnection(url, id, pw);
		
		String sql = "SELECT	* "
				   + "FROM	buru "
				   + "WHERE	nickname = ?";
		PreparedStatement pstmt = conn.prepareStatement(sql);
		pstmt.setString(1, nickName);
		pstmt.executeQuery();
		ResultSet rs = pstmt.executeQuery();
		
		while(rs.next()) {
			int p1money = rs.getInt("p1money");
			return p1money;
		}
		
		rs.close();
		pstmt.close();
		conn.close();
		return -1;
	}
	
	// 저장되어있던 플레이어2의 돈 
	static int getP2Money(String nickName) throws Exception {
		String driver = "oracle.jdbc.driver.OracleDriver";
		String url = "jdbc:oracle:thin:@localhost:1521:xe";
		String id = "scott";
		String pw = "tiger";
		Class.forName(driver);
		Connection conn = DriverManager.getConnection(url, id, pw);
		
		String sql = "SELECT	* "
				   + "FROM	buru "
				   + "WHERE	nickname = ?";
		PreparedStatement pstmt = conn.prepareStatement(sql);
		pstmt.setString(1, nickName);
		pstmt.executeQuery();
		ResultSet rs = pstmt.executeQuery();
		
		while(rs.next()) {
			int p2money = rs.getInt("p2money");
			return p2money;
		}
		
		rs.close();
		pstmt.close();
		conn.close();
		return -1;
	}
	
	// 사용자가 저장했던 게임 속 건물 소유 데이터 불러오기 
	static void loadBuilding(String nickName) throws Exception {
		String driver = "oracle.jdbc.driver.OracleDriver";
		String url = "jdbc:oracle:thin:@localhost:1521:xe";
		String id = "scott";
		String pw = "tiger";
		Class.forName(driver);
		Connection conn = DriverManager.getConnection(url, id, pw);
		
		String sql = "SELECT	* "
				   + "FROM	buildingown "
				   + "WHERE	nickname = ?";
		PreparedStatement pstmt = conn.prepareStatement(sql);
		pstmt.setString(1, nickName);
		pstmt.executeQuery();
		ResultSet rs = pstmt.executeQuery();
		
		while(rs.next()) {
			for(int i=1; i<=11; i++) {
				int own = rs.getInt("map_" + i);
				if(own != 0) buildingOX[mapX(i)][mapY(i)] = 1;
				if(own == 1) {
					p1Building[i][0] = 1;
					loadBuildingFloor(nickName, i, 1);
				}
				else if(own == 2) {
					p2Building[i][0] = 1;
					loadBuildingFloor(nickName, i, 2);
				}
			}
		}
		
		rs.close();
		pstmt.close();
		conn.close();
	}
	
	// 사용자가 저장했던 게임 속 건물 층수 데이터 불러오기 
	static void loadBuildingFloor(String nickName, int i, int who) throws Exception {
		String driver = "oracle.jdbc.driver.OracleDriver";
		String url = "jdbc:oracle:thin:@localhost:1521:xe";
		String id = "scott";
		String pw = "tiger";
		Class.forName(driver);
		Connection conn = DriverManager.getConnection(url, id, pw);
		
		String sql = "SELECT	* "
				   + "FROM	buildingfloor "
				   + "WHERE	nickname = ?";
		PreparedStatement pstmt = conn.prepareStatement(sql);
		pstmt.setString(1, nickName);
		pstmt.executeQuery();
		ResultSet rs = pstmt.executeQuery();
		
		while(rs.next()) {
			int floor = rs.getInt("map_" + i);
			buildingFloor[mapX(i)][mapY(i)] = floor;
			if(who == 1) p1Building[i][1] = floor;
			else p2Building[i][1] = floor;
		}
		
		rs.close();
		pstmt.close();
		conn.close();
	}
	
	
	
	
	}

	
	
		


