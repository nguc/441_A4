import java.util.Timer;
import java.util.TimerTask;

public class TimeoutHandler extends TimerTask {

	private Router router;
	
	
	public TimeoutHandler(Router router){
		this.router = router;
	}
	
	
	@Override
	public void run() {
		try 
		{
			if (!router.quit) {
				router.updateNeighbours();
				restart();
			}
		} catch (Exception e) {  e.printStackTrace();  }
	}
	
	public void restart() {
		//System.out.println("restting the timer");
		router.timer.cancel();
		router.timer = new Timer();
		router.handler = new TimeoutHandler(router);
		router.timer.schedule(router.handler , router.updateInterval);
	}
}
