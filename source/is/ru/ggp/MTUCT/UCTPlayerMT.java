package is.ru.ggp.MTUCT;

import org.eclipse.palamedes.gdl.core.model.IMove;
import org.eclipse.palamedes.gdl.core.simulation.StrategyFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.eclipse.palamedes.gdl.connection.Message;
import org.eclipse.palamedes.gdl.connection.Player;
import org.eclipse.palamedes.gdl.connection.PlayerServer;
import org.eclipse.palamedes.gdl.core.model.GameFactory;
import org.eclipse.palamedes.gdl.core.model.IGame;
import org.eclipse.palamedes.gdl.core.model.utils.Game;
import org.eclipse.palamedes.gdl.core.simulation.IStrategy;

public final class UCTPlayerMT extends Player
{


    static
    {
        StrategyFactory.getInstance().addDescription(
                "UCTStrategyMultiThread",
                UCTStrategyMultiThread.class.getCanonicalName(),
                "Simulates games and chooses the most rewarded path with 8 threads running up and down the tree.");
    }

    private UCTStrategyMultiThread puct;


    /**
     * This method is called when a new match begins.
     * <p/>
     * <br/>
     * msg="(START MATCHID ROLE GAMEDESCRIPTION STARTCLOCK PLAYCLOCK)"<br/>
     * e.g. msg="(START tictactoe1 white ((role white) (role black) ...) 1800 120)" means:
     * <ul>
     * <li>the current match is called "tictactoe1"</li>
     * <li>your role is "white",</li>
     * <li>
     * after at most 1800 seconds, you have to return from the
     * commandStart method
     * </li>
     * <li>for each move you have 120 seconds</li>
     * </ul>
     */
    public void commandStart(Message msg)
    {

        // create the clock
        int playClock = msg.getPlayClock() - 1;
        int startClock = msg.getStartClock() - 1;

        System.out.println("Start Clock " + startClock);
        System.out.println("Play  Clock " + playClock);

        // get the game from the database
        /** XXX: You can change here between GameFactory.JAVAPROVER, GameFactory.JOCULAR
         *       and GameFactory.PROLOG. Both Stanford resolvers have some
         *       drawbacks. Currently JAVAPROVER is slower whereas JOCULAR
         *       does not support the full range of special characters like '+'.
         *       GameFactory.PROLOG is probably the fastest option, but you need
         *       to have Eclipse-Prolog installed (http://www.eclipse-clp.org/). */
        GameFactory factory = GameFactory.getInstance();
        IGame runningGame = factory.createGame(GameFactory.JAVAPROVER,
                                               msg.getGameDescription());
        //IGame                 runningGame = factory.createGame( GameFactory.JOCULAR,
        //                                                      msg.getGameDescription() );
        //IGame                 runningGame = factory.createGame( GameFactory.PROLOGPROVER,
        //                                                      msg.getGameDescription() );
        System.out.println("UCTStrategyMultiThread created the game with JAVAPROVER.");


        /** XXX: If you implement another strategy here is the place to instantiate it */
        try
        {
            puct = new UCTStrategyMultiThread(
                    runningGame,
                    runningGame.getState(runningGame.getTree().getRootNode()),
                    50,
                    1,
                    5);
        } catch (InterruptedException e)
        {
            System.out.println("Interrupted during Construction");
            e.printStackTrace();
        }
        IStrategy strategy = (IStrategy) puct;//StrategyFactory.getInstance().createStrategy("UCTStrategyMultiThread");

        System.out.println("UCTStrategyMultiThread created the strategy " +
                           strategy.getClass().getSimpleName() +
                           ".");

        System.out.println("UCTStrategyMultiThread starts contemplate while doing yoga.");

        // create a match

        realMatch = createRealMatch(msg.getMatchId(),
                                    runningGame,
                                    strategy,
                                    msg.getRole(),
                                    startClock,
                                    playClock);
        System.out.println("UCTStrategyMultiThread created the match.");
        System.out.println("UCTStrategyMultiThread is prepared to start the game.");
        System.out.println("stats:" + runningGame.getStatistic());

        puct.setRole(realMatch.getRole());
        puct.run();
    }


    /**
     * This method is called once for each move<br/>
     * <br/>
     * msg = "(PLAY MATCHID JOINTMOVE)<br/>
     * JOINTMOVE will be NIL for the first PLAY message and the list of the
     * moves of all the players in the previous state<br/>
     * e.g. msg="(PLAY tictactoe1 NIL)" for the first PLAY message
     * or msg="(PLAY tictactoe1 ((MARK 1 2) NOOP))" if white marked cell (1,2)
     * and black did a "noop".<br/>
     *
     * @return the move of this player
     */
    public String commandPlay(Message msg)
    {
        System.out.println("Just checking to see where the commandPlay happens");
        checkMatchId(msg);

        // only make a turn if the move list is not empty
        // is it empty it means we hit the first play message, setting the
        // initial state is done while constructing the match object
        if (msg.hasMoves())
        {
            System.out.println("Moves from GameMaster: " +
                               Arrays.toString(msg.getMoves()));

            String[] prepared = prepareMoves(msg);

            // got the initial NIL in case of length == 0
            if (prepared.length != 0)
            {
                // prepare moves
                realMatch.makeTurn(prepared);

                try
                {
                    System.out.print("Advancing root, moves");
                    for (String string : msg.getMoves())
                        System.out.print(":" + string);
                    System.out.println();
                    puct.advanceRoot(msg.getMoves()); /* <-------------- */
                } catch (InterruptedException e)
                {

                }
            }
        }


        // real work is done here
        //String move = realMatch.getMove().getMove().toUpperCase();  /* <-------------- */

//        while (!realMatch.getTimer().interrupted()) /* <-------------- */
//        {
//            try
//            {
//                Thread.sleep(0);                   /* <-------------- */
//            } catch (InterruptedException e)
//            {
//                System.out.println("I should be returning move now (I think)");
//
//                break;
//            }
//        }                                     /* <-------------- */
        String move = realMatch.getMove().getMove().toUpperCase();//puct.getBestMove(realMatch.getRole());           /* <-------------- */

        // logging the resulting move
        System.out.println("Our move: " + move);
        System.out.println("stats:" + ((Game) realMatch.getGame()).getStatistic());
        return move;//.getMove().toUpperCase();               /* <-------------- */
    }


    /**
     * This method is called if the match is over
     * <p/>
     * msg="(STOP MATCHID JOINTMOVE)
     */
    public void commandStop(Message msg)
    {
        checkMatchId(msg);
        puct.stop();
        // adds the moves to the match
        if (msg.hasMoves())

            // real work is done here
            realMatch.makeTurn(prepareMoves(msg));

        // check if we agree to be in a final state and log the result
        if (!realMatch.getCurrentNode().isTerminal())
            System.out.println("Game stopped but not finished");
        else
        {
            System.out.println("Game finished");
            try
            {
                int[] goalvalues;
                goalvalues = realMatch.getGame().getGoalValues(realMatch.getCurrentNode());
                System.out.println("goal:");
                for (int i = 0; i < goalvalues.length; ++i)
                {
                    System.out.print(goalvalues[i] + " ");
                }
                System.out.println();
            } catch (InterruptedException e)
            {
                System.out.println("Timeout while computing goal values:");
                e.printStackTrace();
            }
        }
    }

    /**
     * starts the game player and waits for messages from the game master <br>
     * Command line options: --port=<port> --slave=<true|false>
     */
    public static void main(String[] args)
    {

        /* create and start player server */
        try
        {
            new PlayerServer(new UCTPlayerMT(),
                             PlayerServer.getOptions(args)).waitForExit();
        } catch (IOException ex)
        {
            ex.printStackTrace();
            System.exit(-1);
        }
    }

}