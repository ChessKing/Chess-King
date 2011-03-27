package org.petero.droidfish.gamelogic;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.petero.droidfish.PGNOptions;
import org.petero.droidfish.gamelogic.Game.GameState;
import org.petero.droidfish.gamelogic.GameTree.Node;
import org.petero.droidfish.gamelogic.GameTree.PgnScanner;

public class GameTreeTest {

	@Test
	public final void testGameTree() throws ChessParseError {
		GameTree gt = new GameTree(null);
		Position expectedPos = TextIO.readFEN(TextIO.startPosFEN);
		assertEquals(expectedPos, gt.currentPos);

		List<Move> varList = gt.variations();
		assertEquals(0, varList.size());

		int varNo = gt.addMove("e4", "", 0, "", "");
		assertEquals(0, varNo);
		assertEquals(expectedPos, gt.currentPos);

		gt.goForward(varNo);
		Move move = TextIO.UCIstringToMove("e2e4");
		UndoInfo ui = new UndoInfo();
		expectedPos.makeMove(move, ui);
		assertEquals(expectedPos, gt.currentPos);

		gt.goBack();
		expectedPos.unMakeMove(move, ui);
		assertEquals(expectedPos, gt.currentPos);
		
		varNo = gt.addMove("d4", "", 0, "", "");
		assertEquals(1, varNo);
		assertEquals(expectedPos, gt.currentPos);
		varList = gt.variations();
		assertEquals(2, varList.size());

		gt.goForward(varNo);
		move = TextIO.UCIstringToMove("d2d4");
		expectedPos.makeMove(move, ui);
		assertEquals(expectedPos, gt.currentPos);

		varNo = gt.addMove("g8f6", "", 0, "", "");
		assertEquals(0, varNo);
		assertEquals(expectedPos, gt.currentPos);
		varList = gt.variations();
		assertEquals(1, varList.size());
		
		gt.goForward(-1);
		Move move2 = TextIO.UCIstringToMove("g8f6");
		UndoInfo ui2 = new UndoInfo();
		expectedPos.makeMove(move2, ui2);
		assertEquals(expectedPos, gt.currentPos);
		assertEquals("Nf6", gt.currentNode.moveStr);

		gt.goBack();
		assertEquals("d4", gt.currentNode.moveStr);
		gt.goBack();
		expectedPos.unMakeMove(move2, ui2);
		expectedPos.unMakeMove(move, ui);
		assertEquals(expectedPos, gt.currentPos);
		assertEquals("", gt.currentNode.moveStr);
		
		gt.goForward(-1); // Should remember that d2d4 was last visited branch
		expectedPos.makeMove(move, ui);
		assertEquals(expectedPos, gt.currentPos);
		
		byte[] serialState = gt.toByteArray();
		gt = new GameTree(null);
		gt.fromByteArray(serialState);
		assertEquals(expectedPos, gt.currentPos);

		gt.goBack();
		expectedPos.unMakeMove(move, ui);
		assertEquals(expectedPos, gt.currentPos);
		varList = gt.variations();
		assertEquals(2, varList.size());
	}

	private final String getMoveListAsString(GameTree gt) {
		StringBuilder ret = new StringBuilder();
		Pair<List<Node>, Integer> ml = gt.getMoveList();
		List<Node> lst = ml.first;
		final int numMovesPlayed = ml.second;
		for (int i = 0; i < lst.size(); i++) {
			if (i == numMovesPlayed)
				ret.append('*');
			if (i > 0)
				ret.append(' ');
			ret.append(lst.get(i).moveStr);
		}
		if (lst.size() == numMovesPlayed)
			ret.append('*');
		return ret.toString();
	}

	@Test
	public final void testGetMoveList() throws ChessParseError {
		GameTree gt = new GameTree(null);
		gt.addMove("e4", "", 0, "", "");
		gt.addMove("d4", "", 0, "", "");
		assertEquals("*e4", getMoveListAsString(gt));

		gt.goForward(0);
		assertEquals("e4*", getMoveListAsString(gt));

		gt.addMove("e5", "", 0, "", "");
		gt.addMove("c5", "", 0, "", "");
		assertEquals("e4* e5", getMoveListAsString(gt));

		gt.goForward(1);
		assertEquals("e4 c5*", getMoveListAsString(gt));

		gt.addMove("Nf3", "", 0, "", "");
		gt.addMove("d4", "", 0, "", "");
		assertEquals("e4 c5* Nf3", getMoveListAsString(gt));

		gt.goForward(1);
		assertEquals("e4 c5 d4*", getMoveListAsString(gt));
		
		gt.goBack();
		assertEquals("e4 c5* d4", getMoveListAsString(gt));

		gt.goBack();
		assertEquals("e4* c5 d4", getMoveListAsString(gt));

		gt.goBack();
		assertEquals("*e4 c5 d4", getMoveListAsString(gt));

		gt.goForward(1);
		assertEquals("d4*", getMoveListAsString(gt));
		
		gt.goBack();
		assertEquals("*d4", getMoveListAsString(gt));
		
		gt.goForward(0);
		assertEquals("e4* c5 d4", getMoveListAsString(gt));
	}

	@Test
	public final void testReorderVariation() throws ChessParseError {
		GameTree gt = new GameTree(null);
		gt.addMove("e4", "", 0, "", "");
		gt.addMove("d4", "", 0, "", "");
		gt.addMove("c4", "", 0, "", "");
		assertEquals("e4 d4 c4", getVariationsAsString(gt));
		assertEquals(0, gt.currentNode.defaultChild);
		
		gt.reorderVariation(1, 0);
		assertEquals("d4 e4 c4", getVariationsAsString(gt));
		assertEquals(1, gt.currentNode.defaultChild);
		
		gt.reorderVariation(0, 2);
		assertEquals("e4 c4 d4", getVariationsAsString(gt));
		assertEquals(0, gt.currentNode.defaultChild);

		gt.reorderVariation(1, 2);
		assertEquals("e4 d4 c4", getVariationsAsString(gt));
		assertEquals(0, gt.currentNode.defaultChild);

		gt.reorderVariation(0, 1);
		assertEquals("d4 e4 c4", getVariationsAsString(gt));
		assertEquals(1, gt.currentNode.defaultChild);
	}

	@Test
	public final void testDeleteVariation() throws ChessParseError {
		GameTree gt = new GameTree(null);
		gt.addMove("e4", "", 0, "", "");
		gt.addMove("d4", "", 0, "", "");
		gt.addMove("c4", "", 0, "", "");
		gt.addMove("f4", "", 0, "", "");
		gt.deleteVariation(0);
		assertEquals("d4 c4 f4", getVariationsAsString(gt));
		assertEquals(0, gt.currentNode.defaultChild);
		
		gt.reorderVariation(0, 2);
		assertEquals("c4 f4 d4", getVariationsAsString(gt));
		assertEquals(2, gt.currentNode.defaultChild);
		gt.deleteVariation(1);
		assertEquals("c4 d4", getVariationsAsString(gt));
		assertEquals(1, gt.currentNode.defaultChild);

		gt.addMove("g4", "", 0, "", "");
		gt.addMove("h4", "", 0, "", "");
		assertEquals("c4 d4 g4 h4", getVariationsAsString(gt));
		assertEquals(1, gt.currentNode.defaultChild);
		gt.reorderVariation(1, 2);
		assertEquals("c4 g4 d4 h4", getVariationsAsString(gt));
		assertEquals(2, gt.currentNode.defaultChild);
		gt.deleteVariation(2);
		assertEquals("c4 g4 h4", getVariationsAsString(gt));
		assertEquals(0, gt.currentNode.defaultChild);
	}

	private final String getVariationsAsString(GameTree gt) {
		StringBuilder ret = new StringBuilder();
		List<Move> vars = gt.variations();
		for (int i = 0; i < vars.size(); i++) {
			if (i > 0)
				ret.append(' ');
			String moveStr = TextIO.moveToString(gt.currentPos, vars.get(i), false);
			ret.append(moveStr);
		}
		return ret.toString();
	}
	
	@Test
	public final void testGetRemainingTime() throws ChessParseError {
		GameTree gt = new GameTree(null);
		int initialTime = 60000;
		assertEquals(initialTime, gt.getRemainingTime(true, initialTime));
		assertEquals(initialTime, gt.getRemainingTime(false, initialTime));
		
		gt.addMove("e4", "", 0, "", "");
		gt.goForward(-1);
		assertEquals(initialTime, gt.getRemainingTime(true, initialTime));
		assertEquals(initialTime, gt.getRemainingTime(false, initialTime));
		gt.setRemainingTime(45000);
		assertEquals(45000, gt.getRemainingTime(true, initialTime));
		assertEquals(initialTime, gt.getRemainingTime(false, initialTime));

		gt.addMove("e5", "", 0, "", "");
		assertEquals(45000, gt.getRemainingTime(true, initialTime));
		assertEquals(initialTime, gt.getRemainingTime(false, initialTime));

		gt.goForward(-1);
		assertEquals(45000, gt.getRemainingTime(true, initialTime));
		assertEquals(initialTime, gt.getRemainingTime(false, initialTime));

		gt.addMove("Nf3", "", 0, "", "");
		gt.goForward(-1);
		gt.addMove("Nc6", "", 0, "", "");
		gt.goForward(-1);
		assertEquals(45000, gt.getRemainingTime(true, initialTime));
		assertEquals(initialTime, gt.getRemainingTime(false, initialTime));

		gt.setRemainingTime(30000);
		assertEquals(45000, gt.getRemainingTime(true, initialTime));
		assertEquals(30000, gt.getRemainingTime(false, initialTime));

		gt.addMove("Bb5", "", 0, "", "");
		gt.goForward(-1);
		gt.setRemainingTime(20000);
		assertEquals(20000, gt.getRemainingTime(true, initialTime));
		assertEquals(30000, gt.getRemainingTime(false, initialTime));

		gt.addMove("a6", "", 0, "", "");
		gt.goForward(-1);
		gt.setRemainingTime(15000);
		assertEquals(20000, gt.getRemainingTime(true, initialTime));
		assertEquals(15000, gt.getRemainingTime(false, initialTime));

		gt.goBack();
		assertEquals(20000, gt.getRemainingTime(true, initialTime));
		assertEquals(30000, gt.getRemainingTime(false, initialTime));

		gt.goBack();
		assertEquals(45000, gt.getRemainingTime(true, initialTime));
		assertEquals(30000, gt.getRemainingTime(false, initialTime));

		gt.goBack();
		assertEquals(45000, gt.getRemainingTime(true, initialTime));
		assertEquals(initialTime, gt.getRemainingTime(false, initialTime));
		
		gt.goBack();
		gt.goBack();
		gt.goBack();
		assertEquals(initialTime, gt.getRemainingTime(true, initialTime));
		assertEquals(initialTime, gt.getRemainingTime(false, initialTime));
	}

	private final List<PgnToken> getAllTokens(String s) {
		PgnScanner sc = new PgnScanner(s);
		List<PgnToken> ret = new ArrayList<PgnToken>();
		while (true) {
			PgnToken tok = sc.nextToken();
			if (tok.type == PgnToken.EOF)
				break;
			ret.add(tok);
		}
		return ret;
	}

	@Test
	public final void testPgnScanner() throws ChessParseError {
		List<PgnToken> lst = getAllTokens("a\nb\n%junk\nc3"); // a b c3
		assertEquals(3, lst.size());
		assertEquals(PgnToken.SYMBOL, lst.get(0).type);
		assertEquals("a", lst.get(0).token);
		assertEquals(PgnToken.SYMBOL, lst.get(1).type);
		assertEquals("b", lst.get(1).token);
		assertEquals(PgnToken.SYMBOL, lst.get(2).type);
		assertEquals("c3", lst.get(2).token);

		lst = getAllTokens("e2 ; e5\nc5"); // e2 comment c5
		assertEquals(3, lst.size());
		assertEquals("e2",			   lst.get(0).token);
		assertEquals(PgnToken.COMMENT, lst.get(1).type);
		assertEquals(" e5",			   lst.get(1).token);
		assertEquals("c5",			   lst.get(2).token);

		lst = getAllTokens("e4?? { comment ; } e5!?"); // e4?? comment e5!?
		assertEquals(3, lst.size());
		assertEquals("e4??",        lst.get(0).token);
		assertEquals(" comment ; ", lst.get(1).token);
		assertEquals("e5!?",        lst.get(2).token);

		lst = getAllTokens("e4! { comment { } e5?"); // e4! comment e5?
		assertEquals(3, lst.size());
		assertEquals("e4!",         lst.get(0).token);
		assertEquals(" comment { ", lst.get(1).token);
		assertEquals("e5?",         lst.get(2).token);
		
		lst = getAllTokens("e4(c4 {(()\\} c5 ( e5))Nf6"); // e4 ( c4 comment c5 ( e5 ) ) Nf6
		assertEquals(10, lst.size());
		assertEquals("e4",                 lst.get(0).token);
		assertEquals(PgnToken.LEFT_PAREN,  lst.get(1).type);
		assertEquals("c4",                 lst.get(2).token);
		assertEquals("(()\\",       	   lst.get(3).token);
		assertEquals("c5",                 lst.get(4).token);
		assertEquals(PgnToken.LEFT_PAREN,  lst.get(5).type);
		assertEquals("e5",                 lst.get(6).token);
		assertEquals(PgnToken.RIGHT_PAREN, lst.get(7).type);
		assertEquals(PgnToken.RIGHT_PAREN, lst.get(8).type);
		assertEquals("Nf6",                lst.get(9).token);

		lst = getAllTokens("[a \"string\"]"); // [ a string ]
		assertEquals(4, lst.size());
		assertEquals(PgnToken.LEFT_BRACKET,  lst.get(0).type);
		assertEquals("a",					 lst.get(1).token);
		assertEquals(PgnToken.STRING,        lst.get(2).type);
		assertEquals("string",               lst.get(2).token);
		assertEquals(PgnToken.RIGHT_BRACKET, lst.get(3).type);
		
		lst = getAllTokens("[a \"str\\\"in\\\\g\"]"); // [ a str"in\g ]
		assertEquals(4, lst.size());
		assertEquals(PgnToken.LEFT_BRACKET,  lst.get(0).type);
		assertEquals("a",					 lst.get(1).token);
		assertEquals(PgnToken.STRING,        lst.get(2).type);
		assertEquals("str\"in\\g",           lst.get(2).token);
		assertEquals(PgnToken.RIGHT_BRACKET, lst.get(3).type);

		lst = getAllTokens("1...Nf6$23Nf3 12 e4_+#=:-*"); // 1 . . . Nf6 $23 Nf3 12 e4_+#=:- *
		assertEquals(10, lst.size());
		assertEquals(PgnToken.INTEGER,  lst.get(0).type);
		assertEquals("1",				lst.get(0).token);
		assertEquals(PgnToken.PERIOD,   lst.get(1).type);
		assertEquals(PgnToken.PERIOD,   lst.get(2).type);
		assertEquals(PgnToken.PERIOD,   lst.get(3).type);
		assertEquals("Nf6",             lst.get(4).token);
		assertEquals(PgnToken.NAG,      lst.get(5).type);
		assertEquals("23",				lst.get(5).token);
		assertEquals("Nf3",             lst.get(6).token);
		assertEquals(PgnToken.INTEGER,  lst.get(7).type);
		assertEquals("12",				lst.get(7).token);
		assertEquals("e4_+#=:-",        lst.get(8).token);
		assertEquals(PgnToken.ASTERISK, lst.get(9).type);

		lst = getAllTokens("1/2-1/2 1-0 0-1");
		assertEquals(3, lst.size());
		assertEquals(PgnToken.SYMBOL,   lst.get(0).type);
		assertEquals("1/2-1/2",			lst.get(0).token);
		assertEquals(PgnToken.SYMBOL,   lst.get(1).type);
		assertEquals("1-0",				lst.get(1).token);
		assertEquals(PgnToken.SYMBOL,   lst.get(2).type);
		assertEquals("0-1",				lst.get(2).token);
		
		// Test invalid data, unterminated tokens
		lst = getAllTokens("e4 e5 ; ( )"); // e4 e5 comment
		assertEquals(3, lst.size());
		assertEquals(PgnToken.SYMBOL,   lst.get(0).type);
		assertEquals("e4",				lst.get(0).token);
		assertEquals(PgnToken.SYMBOL,   lst.get(1).type);
		assertEquals("e5",				lst.get(1).token);
		assertEquals(PgnToken.COMMENT,  lst.get(2).type);
		assertEquals(" ( )",			lst.get(2).token);
		
		lst = getAllTokens("e4 e5 {"); // e4 e5 ?
		assertTrue(lst.size() >= 2);
		assertEquals(PgnToken.SYMBOL,   lst.get(0).type);
		assertEquals("e4",				lst.get(0).token);
		assertEquals(PgnToken.SYMBOL,   lst.get(1).type);
		assertEquals("e5",				lst.get(1).token);

		lst = getAllTokens("e4 e5 \""); // e4 e5 ?
		assertTrue(lst.size() >= 2);
		assertEquals(PgnToken.SYMBOL,   lst.get(0).type);
		assertEquals("e4",				lst.get(0).token);
		assertEquals(PgnToken.SYMBOL,   lst.get(1).type);
		assertEquals("e5",				lst.get(1).token);
		
		// Test that reading beyond EOF produces more EOF tokens
		PgnScanner sc = new PgnScanner("e4 e5");
		assertEquals(PgnToken.SYMBOL, sc.nextToken().type);
		assertEquals(PgnToken.SYMBOL, sc.nextToken().type);
		assertEquals(PgnToken.EOF,    sc.nextToken().type);
		assertEquals(PgnToken.EOF,    sc.nextToken().type);
		assertEquals(PgnToken.EOF,    sc.nextToken().type);
	}

	@Test
	public final void testReadPGN() throws ChessParseError {
		GameTree gt = new GameTree(null);
		PGNOptions options = new PGNOptions();
		options.imp.variations = true;
		options.imp.comments = true;
		options.imp.nag = true;
		boolean res = gt.readPGN("", options);
		assertEquals(false, res);

		res = gt.readPGN("[White \"a\"][Black \"b\"] {comment} e4 {x}", options);
		assertEquals(true, res);
		assertEquals("a", gt.white);
		assertEquals("b", gt.black);
		assertEquals("e4", getVariationsAsString(gt));
		gt.goForward(0);
		assertEquals("comment", gt.currentNode.preComment);
		assertEquals("x", gt.currentNode.postComment);

		res = gt.readPGN("e4 e5 Nf3", options);
		assertEquals(true, res);
		assertEquals("e4", getVariationsAsString(gt));
		gt.goForward(0);
		assertEquals("e5", getVariationsAsString(gt));
		gt.goForward(0);
		assertEquals("Nf3", getVariationsAsString(gt));

		res = gt.readPGN("e4 e5 (c5 (c6) d4) (d5) Nf3", options);
		assertEquals(true, res);
		assertEquals("e4", getVariationsAsString(gt));
		gt.goForward(0);
		assertEquals("e5 c5 c6 d5", getVariationsAsString(gt));
		gt.goForward(0);
		assertEquals("Nf3", getVariationsAsString(gt));

		res = gt.readPGN("e4 e5 (c5 (c3) d4 (Nc3)) (d5) Nf3", options); // c3 invalid, should be removed
		assertEquals(true, res);
		assertEquals("e4", getVariationsAsString(gt));
		gt.goForward(0);
		assertEquals("e5 c5 d5", getVariationsAsString(gt));
		gt.goForward(1);
		assertEquals("d4 Nc3", getVariationsAsString(gt));

		res = gt.readPGN("e4 + e5", options); // Extra + should be ignored
		assertEquals(true, res);
		assertEquals("e4", getVariationsAsString(gt));
		gt.goForward(0);
		assertEquals("e5", getVariationsAsString(gt));
		
		// Test for broken PGN headers: [White "A "good" player"]
		res = gt.readPGN("[White \"A \"good\" player\"]\ne4", options);
		assertEquals(true, res);
		assertEquals("A \"good\" player", gt.white);
		assertEquals("e4", getVariationsAsString(gt));

		// Test for broken PGN headers: [White "A "good" player"]
		res = gt.readPGN("[White \"A \"good old\" player\"]\ne4", options);
		assertEquals(true, res);
		assertEquals("A \"good old\" player", gt.white);
		assertEquals("e4", getVariationsAsString(gt));
	}

	@Test
	public final void testStringEscape() throws ChessParseError {
		GameTree gt = new GameTree(null);
		PGNOptions options = new PGNOptions();
		gt.white = "test \"x\"";
		String pgn = gt.toPGN(options);
		gt.white = "";
		boolean res = gt.readPGN(pgn, options);
		assertEquals(true, res);
		assertEquals("test \"x\"", gt.white);
	}

	@Test
	public final void testNAG() throws ChessParseError {
		GameTree gt = new GameTree(null);
		PGNOptions options = new PGNOptions();
		options.imp.variations = true;
		options.imp.comments = true;
		options.imp.nag = true;
		boolean res = gt.readPGN("e4! e5 ? Nf3?! Nc6 !? Bb5!! a6?? Ba4 $14", options);
		assertEquals(true, res);

		assertEquals("e4", getVariationsAsString(gt));
		gt.goForward(0);
		assertEquals(1, gt.currentNode.nag);

		assertEquals("e5", getVariationsAsString(gt));
		gt.goForward(0);
		assertEquals(2, gt.currentNode.nag);

		assertEquals("Nf3", getVariationsAsString(gt));
		gt.goForward(0);
		assertEquals(6, gt.currentNode.nag);

		assertEquals("Nc6", getVariationsAsString(gt));
		gt.goForward(0);
		assertEquals(5, gt.currentNode.nag);

		assertEquals("Bb5", getVariationsAsString(gt));
		gt.goForward(0);
		assertEquals(3, gt.currentNode.nag);

		assertEquals("a6", getVariationsAsString(gt));
		gt.goForward(0);
		assertEquals(4, gt.currentNode.nag);

		assertEquals("Ba4", getVariationsAsString(gt));
		gt.goForward(0);
		assertEquals(14, gt.currentNode.nag);
	}
	
	@Test
	public final void testTime() throws ChessParseError {
		GameTree gt = new GameTree(null);
		PGNOptions options = new PGNOptions();
		options.imp.variations = true;
		options.imp.comments = true;
		options.imp.nag = true;
		boolean res = gt.readPGN("e4 { x [%clk 0:0:43] y} e5 {[%clk\n1:2:3]} Nf3 Nc6 {[%clk  -1:2 ]}", options);
		assertEquals(true, res);

		assertEquals("e4", getVariationsAsString(gt));
		gt.goForward(0);
		assertEquals(43000, gt.currentNode.remainingTime);
		assertEquals(" x  y", gt.currentNode.postComment);
		
		assertEquals("e5", getVariationsAsString(gt));
		gt.goForward(0);
		assertEquals(((1*60+2)*60+3)*1000, gt.currentNode.remainingTime);
		assertEquals("", gt.currentNode.postComment);

		assertEquals("Nf3", getVariationsAsString(gt));
		gt.goForward(0);
		assertEquals(Integer.MIN_VALUE, gt.currentNode.remainingTime);
		assertEquals("", gt.currentNode.postComment);

		assertEquals("Nc6", getVariationsAsString(gt));
		gt.goForward(0);
		assertEquals(-(1*60+2)*1000, gt.currentNode.remainingTime);
		assertEquals("", gt.currentNode.postComment);
	}

	@Test
	public final void testPlayerAction() throws ChessParseError {
		GameTree gt = new GameTree(null);
		int varNo = gt.addMove("--", "resign", 0, "", "");
		assertEquals(0, varNo);

		PGNOptions options = new PGNOptions();

		String pgn = gt.toPGN(options);
		assertEquals(-1, pgn.indexOf("--"));
		
		options.exp.playerAction = true;
		pgn = gt.toPGN(options);
		assertTrue(pgn.indexOf("--") >= 0);

		gt = new GameTree(null);
		gt.readPGN(pgn, options);
		assertEquals("--", getVariationsAsString(gt));
		gt.goForward(0);
		assertEquals(GameState.RESIGN_WHITE, gt.getGameState());
		
		gt = new GameTree(null);
		gt.readPGN("1. -- {[%playeraction resign]}", options);
		assertEquals("--", getVariationsAsString(gt));
		gt.goForward(0);
		assertEquals(GameState.RESIGN_WHITE, gt.getGameState());
		
		gt.readPGN("1. e4 -- {[%playeraction resign]}", options);
		assertEquals("e4", getVariationsAsString(gt));
		gt.goForward(0);
		assertEquals("--", getVariationsAsString(gt));
		gt.goForward(0);
		assertEquals(GameState.RESIGN_BLACK, gt.getGameState());

		// Even if playerActions are not exported, moves corresponding
		// to draw offers must still be exported
		gt = new GameTree(null);
		varNo = gt.addMove("e4", "draw offer", 0, "", "");
		assertEquals(0, varNo);
		assertEquals("e4", getVariationsAsString(gt));
		gt.goForward(0);
		varNo = gt.addMove("e5", "", 0, "", "");
		assertEquals(0, varNo);
		assertEquals("e5", getVariationsAsString(gt));
		gt.goForward(0);
		options.exp.playerAction = false;
		pgn = gt.toPGN(options);
		assertTrue(pgn.indexOf("e4") >= 0);
	}

	@Test
	public final void testGameResult() throws ChessParseError {
		GameTree gt = new GameTree(null);
		int varNo = gt.addMove("e4", "", 0, "", "");
		gt.goForward(varNo);
		varNo = gt.addMove("e5", "", 0, "", "");
		gt.goForward(varNo);
		varNo = gt.addMove("--", "resign", 0, "", "");
		gt.goBack();
		gt.goBack();
		varNo = gt.addMove("d4", "", 0, "", "");
		gt.goForward(varNo);
		varNo = gt.addMove("--", "resign", 0, "", "");
		gt.goForward(varNo);

		// Black has resigned in a variation, but white resigned in the mainline,
		// so the PGN result should be "0-1";
		PGNOptions options = new PGNOptions();
		options.exp.variations = true;
//		options.exp.playerAction = true;
		String pgn = gt.toPGN(options);
		assertTrue(pgn.indexOf("1-0") < 0);
		assertTrue(pgn.indexOf("0-1") >= 0);
	}

	@Test
	public final void testPGNPromotion() throws ChessParseError {
		// PGN standard specifies that promotion moves shall have an = sign 
		// before the promotion piece
		GameTree gt = new GameTree(null);
		gt.setStartPos(TextIO.readFEN("rnbqkbnr/ppPppppp/8/8/8/8/PP1PPPPP/RNBQKBNR w KQkq - 0 1"));
		int varNo = gt.addMove("cxb8N", "", 0, "", "");
		assertEquals(0, varNo);
		varNo = gt.addMove("cxd8R+", "", 0, "", "");
		assertEquals(1, varNo);
		assertEquals("cxb8N cxd8R+", getVariationsAsString(gt)); // Normal short alg notation does not have =
		PGNOptions options = new PGNOptions();
		options.exp.variations = true;
		String pgn = gt.toPGN(options);
		assertTrue(pgn.indexOf("cxb8=N") >= 0);			  // ... but PGN promotions do have the = sign
		assertTrue(pgn.indexOf("cxd8=R+") >= 0);

	}
}
