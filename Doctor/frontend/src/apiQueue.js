// Two independent requests may progress together. Keep the demo DB bounded, never retry writes.
let active=0;
const waiting=[];
function drain(){while(active<2 && waiting.length)waiting.shift()();}
export function apiSlot(signal,operation){
  return new Promise((resolve,reject)=>{
    let started=false,finished=false;
    const abort=()=>finish(new DOMException('Yêu cầu đã hủy','AbortError'));
    const run=()=>{
      if(finished)return;
      started=true;active++;
      Promise.resolve().then(()=>finished?undefined:operation()).then(value=>finish(null,value),error=>finish(error));
    };
    function finish(error,value){
      if(finished)return;finished=true;
      signal?.removeEventListener('abort',abort);
      if(started)active--;else {const index=waiting.indexOf(run);if(index>=0)waiting.splice(index,1);}
      if(error)reject(error);else resolve(value);
      drain();
    }
    if(signal?.aborted){abort();return;}
    signal?.addEventListener('abort',abort,{once:true});
    waiting.push(run);drain();
  });
}
